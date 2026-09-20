package io.github.karunarathnad.webhook.example.controller;

import io.github.karunarathnad.webhook.example.model.Order;
import io.github.karunarathnad.webhook.example.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrder_returnsCreatedOrder_whenRequestIsValid() throws Exception {
        Order created = new Order("order-1", "cust-1", "widget", BigDecimal.TEN, "CREATED");
        when(orderService.createOrder("cust-1", "widget", BigDecimal.TEN)).thenReturn(created);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"cust-1","product":"widget","amount":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"id":"order-1","customerId":"cust-1","product":"widget","amount":10,"status":"CREATED"}
                        """));
    }

    @Test
    void createOrder_returnsBadRequest_whenCustomerIdBlank() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"","product":"widget","amount":10}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_returnsBadRequest_whenAmountNotPositive() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"cust-1","product":"widget","amount":0}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void updateStatus_returnsUpdatedOrder_whenStatusProvided() throws Exception {
        Order updated = new Order("order-1", "customer-1", "Unknown", BigDecimal.ZERO, "SHIPPED");
        when(orderService.updateOrderStatus("order-1", "SHIPPED")).thenReturn(updated);

        mockMvc.perform(put("/orders/order-1/status").param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":"order-1","status":"SHIPPED"}
                        """));
    }

    @Test
    void updateStatus_returnsBadRequest_whenStatusBlank() throws Exception {
        mockMvc.perform(put("/orders/order-1/status").param("status", "  "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void cancelOrder_returnsNoContent_andDelegatesToService() throws Exception {
        mockMvc.perform(delete("/orders/order-1"))
                .andExpect(status().isNoContent());

        verify(orderService).cancelOrder("order-1");
    }
}
