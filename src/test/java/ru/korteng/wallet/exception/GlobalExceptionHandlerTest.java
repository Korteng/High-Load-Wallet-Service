package ru.korteng.wallet.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.korteng.wallet.controller.WalletController;
import ru.korteng.wallet.service.WalletService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bad client input must never end as a 500. */
class GlobalExceptionHandlerTest {

    private static final String WALLET_ID = "11111111-1111-1111-1111-111111111111";

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
    void invalidUuidInPath_is400() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/wallets/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("walletId")));
    }

    @Test
    void invalidQueryValue_is400() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/wallets/" + WALLET_ID + "/operations").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("limit")));
    }

    @Test
    void invalidInitialBalance_is400() throws Exception {
        mockMvc.perform(post("/api/v1/wallet").param("initialBalance", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongHttpMethod_is405() throws Exception {
        mockMvc.perform(delete("/api/v1/wallet/balance"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unsupportedContentType_is415() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/balance").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unknownResource_is404() {
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleNoResource(new NoResourceFoundException(HttpMethod.GET, "actuator/health"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void unexpectedError_isStill500() throws Exception {
        when(walletService.getWallet(any(UUID.class))).thenThrow(new IllegalStateException("boom"));
        mockMvc.perform(get("/api/v1/wallet/wallets/" + WALLET_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера"));
    }
}
