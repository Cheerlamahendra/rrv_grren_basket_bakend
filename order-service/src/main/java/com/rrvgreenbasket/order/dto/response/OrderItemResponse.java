package com.rrvgreenbasket.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderItemResponse {

    private String id;
    private String publicId;
    private String orderItemPublicId;
    private String productPublicId;
    private String productName;
    private String productImage;
    private String unitOfMeasure;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;

    public String getId() {
        return id != null ? id : publicId;
    }

    public String getOrderItemPublicId() {
        return orderItemPublicId != null ? orderItemPublicId : publicId;
    }
}
