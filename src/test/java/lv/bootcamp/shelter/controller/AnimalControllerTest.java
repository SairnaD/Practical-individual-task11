package lv.bootcamp.shelter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lv.bootcamp.shelter.config.SecurityConfig;
import lv.bootcamp.shelter.service.AnimalNotFoundException;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import lv.bootcamp.shelter.dto.*;
import lv.bootcamp.shelter.model.*;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnimalController.class)
@Import(SecurityConfig.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnimalService animalService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(animalService.findById(99L)).thenThrow(new AnimalNotFoundException(99L));

        mockMvc.perform(get("/api/animals/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_shouldReturnAnimalsAsJson() throws Exception {
        when(animalService.findAll()).thenReturn(List.of(
                new AnimalResponse(1L, "Rex", AnimalType.DOG, "Lab", 3, "Friendly", AnimalStatus.AVAILABLE),
                new AnimalResponse(2L, "Mia", AnimalType.CAT, "Siamese", 2, "Calm", AnimalStatus.AVAILABLE)));

        mockMvc.perform(get("/api/animals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Rex"))
                .andExpect(jsonPath("$[1].name").value("Mia"));
    }

    @Test
    void create_shouldReturn400AndSkipServiceWhenBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"name": "", "type": null}
                            """))
                .andExpect(status().isBadRequest());

        verify(animalService, never()).create(any());
    }

    @Test
    void create_shouldReturn201WithCreatedAnimal() throws Exception {
        when(animalService.create(any())).thenReturn(
                new AnimalResponse(1L, "Rex", AnimalType.DOG, "Lab", 3, "Friendly", AnimalStatus.AVAILABLE));

        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AnimalCreateRequest("Rex", AnimalType.DOG, "Lab", 3, "Friendly", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }
}
