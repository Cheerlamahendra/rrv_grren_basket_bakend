package com.rrvgreenbasket.order.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrvgreenbasket.order.client.CartServiceClient;
import com.rrvgreenbasket.order.client.CustomerServiceClient;
import com.rrvgreenbasket.order.client.DeliveryServiceClient;
import com.rrvgreenbasket.order.client.ProductServiceClient;
import com.rrvgreenbasket.order.client.dto.*;
import com.rrvgreenbasket.order.dto.request.AddressDto;
import com.rrvgreenbasket.order.dto.request.CreateOrderRequest;
import com.rrvgreenbasket.order.dto.request.PaymentEventRequest;
import com.rrvgreenbasket.order.dto.response.*;
import com.rrvgreenbasket.order.entity.Order;
import com.rrvgreenbasket.order.entity.OrderBusinessSettings;
import com.rrvgreenbasket.order.entity.OrderItem;
import com.rrvgreenbasket.order.entity.OrderStatusHistory;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.enums.PaymentMethod;
import com.rrvgreenbasket.order.enums.PaymentStatus;
import com.rrvgreenbasket.order.exception.*;
import com.rrvgreenbasket.order.repository.OrderItemRepository;
import com.rrvgreenbasket.order.repository.OrderRepository;
import com.rrvgreenbasket.order.repository.OrderStatusHistoryRepository;
import com.rrvgreenbasket.order.service.OrderBusinessSettingsService;
import com.rrvgreenbasket.order.service.OrderService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderBusinessSettingsService settingsService;
    private final CartServiceClient cartServiceClient;
    private final ProductServiceClient productServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private Order findOrderByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new OrderNotFoundException("Order identifier cannot be empty");
        }
        String trimmed = identifier.trim();
        Optional<Order> order;
        if (trimmed.startsWith("RRV-")) {
            order = orderRepository.findByOrderNumber(trimmed);
            if (order.isEmpty()) {
                order = orderRepository.findByPublicId(trimmed);
            }
        } else {
            order = orderRepository.findByPublicId(trimmed);
            if (order.isEmpty()) {
                order = orderRepository.findByOrderNumber(trimmed);
            }
        }
        return order.orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + identifier));
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String customerPublicId) {
        log.info("Processing order placement for customer: {}", customerPublicId);

        // 1. Idempotency protection
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Order> existingOrder = orderRepository.findByCustomerPublicIdAndIdempotencyKey(
                    customerPublicId, request.getIdempotencyKey().trim());
            if (existingOrder.isPresent()) {
                log.info("Duplicate order request with idempotency key '{}', returning existing order: {}",
                        request.getIdempotencyKey(), existingOrder.get().getOrderNumber());
                return mapToOrderResponse(existingOrder.get());
            }
        }

        // 2. Business cutoff timing validation
        settingsService.validateOrderAcceptanceWindow();

        // 3. Fetch active shopping cart
        CartClientResponse cart = cartServiceClient.getCustomerCart(customerPublicId);
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new OrderValidationException("Cannot place order with an empty shopping cart");
        }

        // 4. Pre-checkout cart validation (checks availability and active status)
        try {
            CartValidationClientResponse cartValidation = cartServiceClient.validateCart(customerPublicId);
            if (cartValidation != null && Boolean.FALSE.equals(cartValidation.getIsValid())) {
                String errorSummary = cartValidation.getItems().stream()
                        .filter(i -> !"VALID".equalsIgnoreCase(i.getStatus()))
                        .map(i -> i.getProductName() + ": " + i.getMessage())
                        .collect(Collectors.joining("; "));
                throw new OrderValidationException("Cart validation failed: " + errorSummary);
            }
        } catch (OrderValidationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Cart validation probe returned warning, continuing with individual product verification: {}", e.getMessage());
        }

        // 5. Retrieve authoritative registered customer profile details
        String registeredCustomerName = null;
        String registeredCustomerMobile = null;
        try {
            CustomerClientResponse profile = customerServiceClient.getCustomerProfile(customerPublicId);
            if (profile != null) {
                registeredCustomerName = profile.getFullName();
                registeredCustomerMobile = profile.getMobileNumber();
                log.info("Authoritative customer details retrieved: name='{}', mobile='{}'",
                        registeredCustomerName, registeredCustomerMobile);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve registered customer profile for '{}': {}", customerPublicId, e.getMessage());
        }

        // 6. Verify delivery address and geographical boundary coverage
        AddressDto inboundAddress = request.getDeliveryAddress();
        if (inboundAddress.getLatitude() == null || inboundAddress.getLongitude() == null) {
            throw new OrderValidationException("Delivery coordinates (latitude and longitude) are mandatory");
        }

        DeliveryAvailabilityClientResponse availability = deliveryServiceClient.checkAvailability(
                inboundAddress.getLatitude().doubleValue(),
                inboundAddress.getLongitude().doubleValue(),
                inboundAddress.getAddressLine()
        );

        if (availability == null || !Boolean.TRUE.equals(availability.getAvailable())) {
            throw new OrderValidationException("Delivery is not available at the selected location");
        }

        // 7. Resolve delivery address fields and ensure proper persistence
        String recipientName = inboundAddress.getRecipientName();
        String recipientMobile = inboundAddress.getRecipientMobile();
        String addressLine1 = inboundAddress.getAddressLine1();
        String addressLine2 = inboundAddress.getAddressLine2();
        String landmark = inboundAddress.getLandmark();
        String city = inboundAddress.getCity();
        String state = inboundAddress.getState();
        String pincode = inboundAddress.getPincode();
        String country = inboundAddress.getCountry() != null ? inboundAddress.getCountry() : "India";
        String addressPublicId = request.getAddressId() != null && !request.getAddressId().isBlank()
                ? request.getAddressId().trim()
                : inboundAddress.getAddressPublicId();

        if (addressPublicId != null && !addressPublicId.isBlank()) {
            // Selected existing address from customer address table
            try {
                AddressClientResponse savedAddr = customerServiceClient.getAddress(customerPublicId, addressPublicId);
                if (savedAddr != null) {
                    if (addressLine1 == null || addressLine1.isBlank()) {
                        addressLine1 = savedAddr.getAddressLine1();
                    }
                    if (addressLine2 == null || addressLine2.isBlank()) {
                        addressLine2 = savedAddr.getAddressLine2();
                    }
                    if (landmark == null || landmark.isBlank()) {
                        landmark = savedAddr.getLandmark();
                    }
                    if (city == null || city.isBlank()) {
                        city = savedAddr.getCity();
                    }
                    if (state == null || state.isBlank()) {
                        state = savedAddr.getState();
                    }
                    if (pincode == null || pincode.isBlank()) {
                        pincode = savedAddr.getPincode();
                    }
                    if (savedAddr.getCountry() != null && !savedAddr.getCountry().isBlank()) {
                        country = savedAddr.getCountry();
                    }
                    // If recipient details were not overridden in the request, use the saved address receiver details
                    if (recipientName == null || recipientName.isBlank()) {
                        recipientName = savedAddr.getReceiverName();
                    }
                    if (recipientMobile == null || recipientMobile.isBlank()) {
                        recipientMobile = savedAddr.getReceiverMobile();
                    }
                    log.info("Selected saved address '{}' loaded. Recipient for order: '{}' ({})",
                            addressPublicId, recipientName, recipientMobile);
                }
            } catch (Exception e) {
                log.warn("Failed to load saved address '{}' from customer-service: {}", addressPublicId, e.getMessage());
            }
        } else if (Boolean.TRUE.equals(request.getSaveAddress())) {
            // New address during checkout; persist into customer address table
            try {
                AddressClientResponse persistedAddr = customerServiceClient.saveAddress(customerPublicId, inboundAddress);
                if (persistedAddr != null && persistedAddr.getId() != null) {
                    addressPublicId = persistedAddr.getId();
                    log.info("Persisted new delivery address to Customer Address Table with publicId='{}'", addressPublicId);
                }
            } catch (Exception e) {
                log.warn("Could not persist new address in Customer Address Table: {}", e.getMessage());
            }
        }

        // Fallbacks for recipient details if still missing
        if ((recipientName == null || recipientName.isBlank()) && registeredCustomerName != null) {
            recipientName = registeredCustomerName;
        }
        if ((recipientMobile == null || recipientMobile.isBlank()) && registeredCustomerMobile != null) {
            recipientMobile = registeredCustomerMobile;
        }

        // Complete delivery address snapshot (fully independent and immutable)
        AddressDto completeDeliverySnapshot = AddressDto.builder()
                .recipientName(recipientName)
                .recipientMobile(recipientMobile)
                .addressLine1(addressLine1)
                .addressLine2(addressLine2)
                .addressLine(inboundAddress.getAddressLine())
                .landmark(landmark)
                .city(city)
                .state(state)
                .pincode(pincode)
                .country(country)
                .latitude(inboundAddress.getLatitude())
                .longitude(inboundAddress.getLongitude())
                .addressPublicId(addressPublicId)
                .build();

        // 8. Calculate cart subtotal from live product prices
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItemsToSave = new ArrayList<>();

        for (CartItemClientDto cartItem : cart.getItems()) {
            ProductDetailClientResponse product = productServiceClient.getProductDetails(cartItem.getProductPublicId());
            if (product == null || Boolean.FALSE.equals(product.getIsAvailableForSale())) {
                throw new OrderValidationException("Product '" + cartItem.getProductName() + "' is currently unavailable for purchase");
            }

            BigDecimal unitPrice = product.getPricePerKg() != null ? product.getPricePerKg() : cartItem.getPricePerKg();
            BigDecimal quantity = cartItem.getQuantity() != null ? cartItem.getQuantity() : cartItem.getQuantityKg();
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new OrderValidationException("Invalid quantity for product: " + cartItem.getProductName());
            }

            BigDecimal itemTotal = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .publicId(UUID.randomUUID().toString())
                    .productPublicId(cartItem.getProductPublicId())
                    .productNameSnapshot(product.getName() != null ? product.getName() : cartItem.getProductName())
                    .productImageSnapshot(product.getProductImageUrl() != null ? product.getProductImageUrl() : cartItem.getProductImageUrl())
                    .unitSnapshot(cartItem.getUnitOfMeasure() != null ? cartItem.getUnitOfMeasure() : "KG")
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .totalPrice(itemTotal)
                    .build();

            orderItemsToSave.add(orderItem);
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        // 9. Validate minimum order amount
        BigDecimal minOrderAmount = availability.getMinimumOrderAmount() != null
                ? availability.getMinimumOrderAmount()
                : BigDecimal.ZERO;

        if (subtotal.compareTo(minOrderAmount) < 0) {
            throw new OrderValidationException(String.format(
                    "Order subtotal (₹%s) is below the minimum order amount of ₹%s required for delivery zone '%s'",
                    subtotal, minOrderAmount, availability.getZoneName()
            ));
        }

        // 10. Calculate delivery charge and free delivery threshold
        BigDecimal deliveryCharge;
        if (availability.getFreeDeliveryThreshold() != null
                && subtotal.compareTo(availability.getFreeDeliveryThreshold()) >= 0) {
            deliveryCharge = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            log.info("Free delivery threshold reached (subtotal={}, threshold={})", subtotal, availability.getFreeDeliveryThreshold());
        } else {
            deliveryCharge = availability.getDeliveryCharge() != null
                    ? availability.getDeliveryCharge().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(deliveryCharge).subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        // 11. Calculate promised next-day delivery date
        ZonedDateTime currentBusinessTime = settingsService.getCurrentBusinessDateTime();
        LocalDate promisedDeliveryDate = settingsService.calculatePromisedDeliveryDate(currentBusinessTime.toLocalDate());

        // 12. Generate public identifiers
        String orderPublicId = UUID.randomUUID().toString();
        String orderNumber = generateOrderNumber(currentBusinessTime.toLocalDate());

        // 13. Configure payment method and initial statuses
        PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.COD;
        OrderStatus initialStatus;
        PaymentStatus initialPaymentStatus;

        if (paymentMethod == PaymentMethod.COD) {
            initialStatus = OrderStatus.CONFIRMED;
            initialPaymentStatus = PaymentStatus.COD_PENDING;
        } else {
            initialStatus = OrderStatus.PLACED;
            initialPaymentStatus = PaymentStatus.PENDING;
        }

        // 14. Serialize complete immutable address snapshot
        String addressSnapshotJson;
        try {
            addressSnapshotJson = objectMapper.writeValueAsString(completeDeliverySnapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize address snapshot: {}", e.getMessage());
            addressSnapshotJson = "{}";
        }

        Order order = Order.builder()
                .publicId(orderPublicId)
                .orderNumber(orderNumber)
                .customerPublicId(customerPublicId)
                .registeredCustomerName(registeredCustomerName)
                .registeredCustomerMobile(registeredCustomerMobile)
                .deliveryZonePublicId(availability.getZonePublicId())
                .deliveryZoneNameSnapshot(availability.getZoneName())
                .orderStatus(initialStatus)
                .paymentStatus(initialPaymentStatus)
                .paymentMethod(paymentMethod)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .deliveryCharge(deliveryCharge)
                .totalAmount(totalAmount)
                .minimumOrderAmountSnapshot(minOrderAmount)
                .deliveryAddressSnapshot(addressSnapshotJson)
                .deliveryLatitude(inboundAddress.getLatitude())
                .deliveryLongitude(inboundAddress.getLongitude())
                .promisedDeliveryDate(promisedDeliveryDate)
                .orderPlacedAt(currentBusinessTime.toLocalDateTime())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        for (OrderItem item : orderItemsToSave) {
            order.addItem(item);
        }

        Order savedOrder = orderRepository.save(order);

        // 15. Record initial status history
        if (paymentMethod == PaymentMethod.COD) {
            log.info("Auto-confirming COD order: {}", savedOrder.getOrderNumber());
            recordStatusHistory(savedOrder.getId(), null, OrderStatus.CONFIRMED, customerPublicId, "Cash on Delivery order placed and confirmed");
        } else {
            recordStatusHistory(savedOrder.getId(), null, OrderStatus.PLACED, customerPublicId, "Order placed successfully by customer");
        }

        // 16. Clear cart after successful order placement
        cartServiceClient.clearCart(customerPublicId);

        log.info("Order created successfully: orderNumber={}, status={}, total={}, promisedDelivery={}",
                savedOrder.getOrderNumber(), savedOrder.getOrderStatus(), savedOrder.getTotalAmount(), savedOrder.getPromisedDeliveryDate());

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getCustomerOrders(String customerPublicId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByCustomerPublicId(customerPublicId, pageable);
        return PageResponse.from(orders.map(this::mapToOrderResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderDetails(String orderPublicId, String customerPublicId) {
        Order order = findOrderByIdentifier(orderPublicId);

        if (!order.getCustomerPublicId().equals(customerPublicId)) {
            throw new UnauthorizedOrderAccessException("You do not have permission to view this order");
        }

        return mapToOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderPublicId, String customerPublicId, String reason) {
        log.info("Customer '{}' cancelling order: {}", customerPublicId, orderPublicId);

        Order order = findOrderByIdentifier(orderPublicId);

        if (!order.getCustomerPublicId().equals(customerPublicId)) {
            throw new UnauthorizedOrderAccessException("You are not authorized to cancel this order");
        }

        if (!order.getOrderStatus().isCancellableByCustomer()) {
            throw new InvalidOrderStatusTransitionException(
                    "Order cannot be cancelled in its current state: " + order.getOrderStatus()
            );
        }

        OrderBusinessSettings settings = settingsService.getSettingsEntity();
        ZoneId storeZone = ZoneId.of(settings.getTimezone() != null ? settings.getTimezone() : "Asia/Kolkata");
        ZonedDateTime nowInStore = ZonedDateTime.now(clock.withZone(storeZone));
        LocalDate currentDate = nowInStore.toLocalDate();
        LocalTime currentTime = nowInStore.toLocalTime();

        LocalDate orderDate = order.getOrderPlacedAt() != null
                ? order.getOrderPlacedAt().toLocalDate()
                : (order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : currentDate);

        if (!orderDate.equals(currentDate)) {
            throw new OrderValidationException("Order cancellation is allowed only on the order placement date.");
        }

        LocalTime opening = settings.getOpeningTime();
        LocalTime closing = settings.getClosingTime();

        if (currentTime.isBefore(opening) || !currentTime.isBefore(closing)) {
            throw new OrderValidationException("Order cancellation is available only during store operating hours.");
        }

        OrderStatus previousStatus = order.getOrderStatus();
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(nowInStore.toLocalDateTime());
        order.setCancellationReason(reason != null && !reason.isBlank() ? reason.trim() : "Cancelled by customer");

        Order updated = orderRepository.save(order);
        recordStatusHistory(updated.getId(), previousStatus, OrderStatus.CANCELLED, customerPublicId, reason);

        log.info("Order '{}' cancelled successfully by customer", orderPublicId);
        return mapToOrderResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatusHistoryResponse getLatestOrderStatus(String orderPublicId, String customerPublicId) {
        Order order = findOrderByIdentifier(orderPublicId);

        if (!order.getCustomerPublicId().equals(customerPublicId)) {
            throw new UnauthorizedOrderAccessException("You do not have permission to view status for this order");
        }

        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        if (history.isEmpty()) {
            return OrderStatusHistoryResponse.builder()
                    .id(0L)
                    .publicId("initial")
                    .newStatus(order.getOrderStatus())
                    .changedBy("SYSTEM")
                    .timestamp(order.getCreatedAt())
                    .build();
        }

        OrderStatusHistory latest = history.get(history.size() - 1);
        return OrderStatusHistoryResponse.builder()
                .id(latest.getId())
                .publicId("history-" + latest.getId())
                .previousStatus(latest.getPreviousStatus())
                .newStatus(latest.getNewStatus())
                .changedBy(latest.getChangedBy())
                .reason(latest.getReason())
                .timestamp(latest.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAdminOrders(
            String search,
            OrderStatus status,
            String customerPublicId,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate deliveryDate,
            Pageable pageable) {

        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("orderStatus"), status));
            }
            if (customerPublicId != null && !customerPublicId.isBlank()) {
                predicates.add(cb.equal(root.get("customerPublicId"), customerPublicId.trim()));
            }
            if (deliveryDate != null) {
                predicates.add(cb.equal(root.get("promisedDeliveryDate"), deliveryDate));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderPlacedAt"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderPlacedAt"), toDate.atTime(23, 59, 59)));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate numberMatch = cb.like(cb.lower(root.get("orderNumber")), term);
                Predicate zoneMatch = cb.like(cb.lower(root.get("deliveryZoneNameSnapshot")), term);
                Predicate nameMatch = cb.like(cb.lower(root.get("registeredCustomerName")), term);
                Predicate mobileMatch = cb.like(cb.lower(root.get("registeredCustomerMobile")), term);
                predicates.add(cb.or(numberMatch, zoneMatch, nameMatch, mobileMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Order> orders = orderRepository.findAll(spec, pageable);
        return PageResponse.from(orders.map(this::mapToOrderResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getAdminOrderDetails(String orderPublicId) {
        Order order = findOrderByIdentifier(orderPublicId);
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(String orderPublicId, OrderStatus newStatus, String reason, String changedBy) {
        log.info("Admin/System '{}' updating status of order '{}' to: {}", changedBy, orderPublicId, newStatus);

        Order order = findOrderByIdentifier(orderPublicId);

        OrderStatus currentStatus = order.getOrderStatus();
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidOrderStatusTransitionException(
                    "Cannot transition order from " + currentStatus + " to " + newStatus
            );
        }

        if (newStatus == OrderStatus.CANCELLED) {
            OrderBusinessSettings settings = settingsService.getSettingsEntity();
            ZoneId storeZone = ZoneId.of(settings.getTimezone() != null ? settings.getTimezone() : "Asia/Kolkata");
            order.setCancelledAt(LocalDateTime.now(clock.withZone(storeZone)));
            order.setCancellationReason(reason);
        }

        order.setOrderStatus(newStatus);
        Order updated = orderRepository.save(order);
        recordStatusHistory(updated.getId(), currentStatus, newStatus, changedBy, reason);

        return mapToOrderResponse(updated);
    }

    @Override
    @Transactional
    public void deleteCancelledOrder(String orderPublicId, String adminUser) {
        log.info("Admin '{}' attempting to delete cancelled order '{}'", adminUser, orderPublicId);
        Order order = findOrderByIdentifier(orderPublicId);

        if (order.getOrderStatus() != OrderStatus.CANCELLED) {
            throw new OrderValidationException("Only cancelled orders can be permanently deleted");
        }

        statusHistoryRepository.deleteByOrderId(order.getId());
        orderRepository.delete(order);
        log.info("Cancelled order '{}' permanently deleted by admin '{}'", orderPublicId, adminUser);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatisticsResponse getAdminStatistics() {
        OrderBusinessSettings settings = settingsService.getSettingsEntity();
        ZoneId storeZone = ZoneId.of(settings.getTimezone() != null ? settings.getTimezone() : "Asia/Kolkata");
        LocalDate today = LocalDate.now(clock.withZone(storeZone));
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.atTime(23, 59, 59);

        long totalOrders = orderRepository.count();
        long todayOrders = orderRepository.countByOrderPlacedAtBetween(startOfToday, endOfToday);
        long pendingOrders = orderRepository.countByOrderStatus(OrderStatus.PLACED);
        long confirmedOrders = orderRepository.countByOrderStatus(OrderStatus.CONFIRMED);
        long processingOrders = orderRepository.countByOrderStatus(OrderStatus.PROCESSING);
        long packedOrders = orderRepository.countByOrderStatus(OrderStatus.PACKED);
        long outForDelivery = orderRepository.countByOrderStatus(OrderStatus.OUT_FOR_DELIVERY);
        long deliveredOrders = orderRepository.countByOrderStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByOrderStatus(OrderStatus.CANCELLED);

        BigDecimal totalRevenue = orderRepository.sumTotalRevenue();
        BigDecimal todayRevenue = orderRepository.sumRevenueBetween(startOfToday, endOfToday);

        return OrderStatisticsResponse.builder()
                .totalOrders(totalOrders)
                .todayOrders(todayOrders)
                .pendingOrders(pendingOrders)
                .confirmedOrders(confirmedOrders)
                .processingOrders(processingOrders)
                .packedOrders(packedOrders)
                .outForDeliveryOrders(outForDelivery)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .todayRevenue(todayRevenue != null ? todayRevenue : BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailsDto getOrderForPayment(String orderPublicId) {
        Order order = findOrderByIdentifier(orderPublicId);

        boolean paymentEligible = order.getOrderStatus() == OrderStatus.PLACED
                && order.getPaymentStatus() == PaymentStatus.PENDING;

        return OrderDetailsDto.builder()
                .orderPublicId(order.getPublicId())
                .customerPublicId(order.getCustomerPublicId())
                .totalAmount(order.getTotalAmount())
                .currency("INR")
                .orderStatus(order.getOrderStatus().name())
                .paymentEligible(paymentEligible)
                .paymentMethod(order.getPaymentMethod().name())
                .build();
    }

    @Override
    @Transactional
    public void handlePaymentEvent(String orderPublicId, PaymentEventRequest request) {
        log.info("Processing payment event '{}' for order: {}", request.getEventType(), orderPublicId);

        Order order = findOrderByIdentifier(orderPublicId);

        if ("PAYMENT_SUCCESS".equalsIgnoreCase(request.getEventType()) || "COMPLETED".equalsIgnoreCase(request.getStatus())) {
            order.setPaymentStatus(PaymentStatus.COMPLETED);

            if (order.getOrderStatus() == OrderStatus.PLACED) {
                OrderStatus prev = order.getOrderStatus();
                order.setOrderStatus(OrderStatus.CONFIRMED);
                recordStatusHistory(order.getId(), prev, OrderStatus.CONFIRMED, "PAYMENT_GATEWAY", "Online payment successful: " + request.getPaymentId());
            }

            orderRepository.save(order);
            log.info("Order '{}' confirmed following successful online payment", orderPublicId);

        } else if ("PAYMENT_FAILED".equalsIgnoreCase(request.getEventType()) || "FAILED".equalsIgnoreCase(request.getStatus())) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            OrderStatus prev = order.getOrderStatus();
            order.setOrderStatus(OrderStatus.FAILED);
            order.setCancellationReason("Payment failure: " + request.getFailureReason());

            orderRepository.save(order);
            recordStatusHistory(order.getId(), prev, OrderStatus.FAILED, "PAYMENT_GATEWAY", "Online payment failed: " + request.getFailureReason());
            log.warn("Order '{}' marked FAILED due to payment failure", orderPublicId);
        }
    }

    private void recordStatusHistory(Long orderId, OrderStatus previous, OrderStatus next, String changedBy, String reason) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(orderId)
                .previousStatus(previous)
                .newStatus(next)
                .changedBy(changedBy)
                .reason(reason)
                .build();
        statusHistoryRepository.save(history);
    }

    private String generateOrderNumber(LocalDate date) {
        String datePart = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        int randomPart = (int) (Math.random() * 90000) + 10000;
        return "RRV-" + datePart + "-" + randomPart;
    }

    private OrderResponse mapToOrderResponse(Order order) {
        AddressDto addressDto = null;
        try {
            if (order.getDeliveryAddressSnapshot() != null) {
                addressDto = objectMapper.readValue(order.getDeliveryAddressSnapshot(), AddressDto.class);
            }
        } catch (Exception e) {
            log.debug("Could not parse address snapshot: {}", e.getMessage());
        }

        String recipientName = addressDto != null ? addressDto.getRecipientName() : null;
        String recipientMobile = addressDto != null ? addressDto.getRecipientMobile() : null;

        String regName = order.getRegisteredCustomerName();
        if ((regName == null || regName.isBlank()) && recipientName != null) {
            regName = recipientName;
        }
        String regMobile = order.getRegisteredCustomerMobile();
        if ((regMobile == null || regMobile.isBlank()) && recipientMobile != null) {
            regMobile = recipientMobile;
        }

        CustomerContactDto customerContact = CustomerContactDto.builder()
                .registeredName(regName)
                .registeredMobile(regMobile)
                .customerPublicId(order.getCustomerPublicId())
                .build();

        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> OrderItemResponse.builder()
                        .id(i.getPublicId())
                        .publicId(i.getPublicId())
                        .orderItemPublicId(i.getPublicId())
                        .productPublicId(i.getProductPublicId())
                        .productName(i.getProductNameSnapshot())
                        .productImage(i.getProductImageSnapshot())
                        .unitOfMeasure(i.getUnitSnapshot())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .totalPrice(i.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        List<OrderStatusHistoryResponse> historyResponses = statusHistoryRepository
                .findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .map(h -> OrderStatusHistoryResponse.builder()
                        .id(h.getId())
                        .publicId("history-" + h.getId())
                        .previousStatus(h.getPreviousStatus())
                        .newStatus(h.getNewStatus())
                        .changedBy(h.getChangedBy())
                        .reason(h.getReason())
                        .timestamp(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        OrderBusinessSettings settings = settingsService.getSettingsEntity();
        ZoneId storeZone = ZoneId.of(settings.getTimezone() != null ? settings.getTimezone() : "Asia/Kolkata");
        ZonedDateTime nowInStore = ZonedDateTime.now(clock.withZone(storeZone));
        LocalDate currentDate = nowInStore.toLocalDate();
        LocalTime currentTime = nowInStore.toLocalTime();

        LocalDate orderDate = order.getOrderPlacedAt() != null
                ? order.getOrderPlacedAt().toLocalDate()
                : (order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : currentDate);

        LocalTime openingTime = settings.getOpeningTime();
        LocalTime closingTime = settings.getClosingTime();
        LocalDateTime cancellationDeadline = orderDate.atTime(closingTime);

        boolean isSameDay = orderDate.equals(currentDate);
        boolean isWithinHours = !currentTime.isBefore(openingTime) && currentTime.isBefore(closingTime);
        boolean isStatusCancellable = order.getOrderStatus() != null && order.getOrderStatus().isCancellableByCustomer();
        boolean cancellationAllowed = isSameDay && isWithinHours && isStatusCancellable;

        return OrderResponse.builder()
                .orderPublicId(order.getPublicId())
                .orderNumber(order.getOrderNumber())
                .customerPublicId(order.getCustomerPublicId())
                .registeredCustomerName(regName)
                .registeredCustomerMobile(regMobile)
                .customer(customerContact)
                .recipientName(recipientName)
                .recipientMobile(recipientMobile)
                .deliveryZonePublicId(order.getDeliveryZonePublicId())
                .deliveryZoneName(order.getDeliveryZoneNameSnapshot())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .deliveryCharge(order.getDeliveryCharge())
                .totalAmount(order.getTotalAmount())
                .minimumOrderAmountSnapshot(order.getMinimumOrderAmountSnapshot())
                .deliveryAddress(addressDto)
                .deliveryLatitude(order.getDeliveryLatitude())
                .deliveryLongitude(order.getDeliveryLongitude())
                .promisedDeliveryDate(order.getPromisedDeliveryDate())
                .orderPlacedAt(order.getOrderPlacedAt())
                .cancelledAt(order.getCancelledAt())
                .cancellationReason(order.getCancellationReason())
                .cancellationAllowed(cancellationAllowed)
                .cancellationDeadline(cancellationDeadline)
                .orderDate(orderDate)
                .currentStoreOpeningTime(openingTime)
                .currentStoreClosingTime(closingTime)
                .items(itemResponses)
                .statusHistory(historyResponses)
                .build();
    }
}
