package lv.bootcamp.shelter;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.*;
import lv.bootcamp.shelter.model.*;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class AdoptionIntegrationTest {

    @Autowired
    private AnimalService animalService;

    @MockitoBean
    private NotificationClient notificationClient;

    @Test
    void adoptionFlow_shouldPersistStatusAndNotify() {
        AnimalResponse created = animalService.create(
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Labrador", 3, "Friendly", null));

        AnimalResponse adopted = animalService.adopt(
                new AdoptionRequest(created.id(), "Anna", "anna@example.com"));

        assertThat(adopted.status()).isEqualTo(AnimalStatus.ADOPTED);
        assertThat(animalService.findById(created.id()).status()).isEqualTo(AnimalStatus.ADOPTED);
        verify(notificationClient)
                .sendAdoptionNotification(created.id(), "Rex", "anna@example.com");
    }
}