package com.rrvgreenbasket.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rrvgreenbasket.order.dto.request.AddressDto;
import com.rrvgreenbasket.order.enums.OrderStatus;
import com.rrvgreenbasket.order.enums.PaymentMethod;
import com.rrvgreenbasket.order.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private List<OrderItemResponse> items;
    private List<OrderStatusHistoryResponse> statusHistory;
}
