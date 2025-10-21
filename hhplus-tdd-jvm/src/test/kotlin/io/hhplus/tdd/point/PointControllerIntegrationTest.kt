package io.hhplus.tdd.point

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PointControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {

    @Test
    fun `charge endpoint updates balance`() {
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id", equalTo(1)))
            .andExpect(jsonPath("$.point", equalTo(100)))

        mockMvc.perform(get("/point/{id}", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point", equalTo(100)))
    }

    @Test
    fun `use endpoint deducts balance`() {
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(300L))),
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/point/{id}/use", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point", equalTo(200)))
    }

    @Test
    fun `history endpoint returns chronological operations`() {
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(200L))),
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/point/{id}/use", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/point/{id}/histories", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()", equalTo(2)))
            .andExpect(jsonPath("$[0].type", equalTo(TransactionType.CHARGE.name)))
            .andExpect(jsonPath("$[0].amount", equalTo(200)))
            .andExpect(jsonPath("$[1].type", equalTo(TransactionType.USE.name)))
            .andExpect(jsonPath("$[1].amount", equalTo(100)))
    }

    @Test
    fun `charge rejects invalid amount`() {
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(150L))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code", equalTo("400")))
    }

    @Test
    fun `use rejects insufficient balance`() {
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(200L))),
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/point/{id}/use", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(300L))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code", equalTo("400")))
    }

    @Test
    fun `charge rejects when exceeding max balance`() {
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(1_000_000L))),
        ).andExpect(status().isOk)

        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code", equalTo("400")))
    }
}
