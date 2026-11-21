package com.rrvgreenbasket.order.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.order.dto.request.AddressDto;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.enums.PaymentMethod;
import com.rrvgreenbasket.order.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {

    private String orderPublicId;
    private String orderNumber;
    private String customerPublicId;

    // Registered customer details snapshot
    private String registeredCustomerName;
    private String registeredCustomerMobile;
    private CustomerContactDto customer;

    // Delivery recipient details snapshot (root convenience fields)
    private String recipientName;
    private String recipientMobile;

    private String deliveryZonePublicId;
    private String deliveryZoneName;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal deliveryCharge;
    private BigDecimal totalAmount;
    private BigDecimal minimumOrderAmountSnapshot;
    private AddressDto deliveryAddress;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;
    private LocalDate promisedDeliveryDate;
    private LocalDateTime orderPlacedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    // Cancellation eligibility fields
    private Boolean cancellationAllowed;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancellationDeadline;

    private LocalDate orderDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime currentStoreOpeningTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime currentStoreClosingTime;

    private List<OrderItemResponse> items;
    private List<OrderStatusHistoryResponse> statusHistory;
}
