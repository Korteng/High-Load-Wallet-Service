package ru.korteng.wallet.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.korteng.wallet.dto.WalletResponse;
import ru.korteng.wallet.exception.GlobalExceptionHandler;
import ru.korteng.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletControllerValidationTest {

    private static final String VALID_ID = "11111111-1111-1111-1111-111111111111";

    private WalletService walletService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WalletController(walletService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void negativeAmount_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_ID, "DEPOSIT", "-5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    void zeroAmount_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_ID, "DEPOSIT", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    void nullAmount_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + VALID_ID + "\",\"operationType\":\"DEPOSIT\",\"amount\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    void missingAmount_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + VALID_ID + "\",\"operationType\":\"DEPOSIT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    void nullId_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":null,\"operationType\":\"DEPOSIT\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("id")));
    }

    @Test
    void nullOperationType_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + VALID_ID + "\",\"operationType\":null,\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("operationType")));
    }

    @Test
    void invalidOperationTypeEnum_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + VALID_ID + "\",\"operationType\":\"TRANSFER\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Невалидный JSON"));
    }

    @Test
    void malformedJson_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Невалидный JSON"));
    }

    @Test
    void validRequest_passesValidationAndReachesService() throws Exception {
        UUID id = UUID.fromString(VALID_ID);
        when(walletService.createOperation(any())).thenReturn(new WalletResponse(id, new BigDecimal("100.00")));

        mockMvc.perform(post("/api/v1/wallet/balance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VALID_ID, "DEPOSIT", "50")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.newBalance").value(100.00));

        verify(walletService).createOperation(any());
    }

    private static String body(String id, String operationType, String amount) {
        return "{\"id\":\"" + id + "\",\"operationType\":\"" + operationType + "\",\"amount\":" + amount + "}";
    }
}