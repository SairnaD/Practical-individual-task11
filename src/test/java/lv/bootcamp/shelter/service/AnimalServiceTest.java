package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AdoptionRequest;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private AnimalService animalService;

    @Captor
    private ArgumentCaptor<List<Long>> idsCaptor;

    @Test
    void create_shouldSaveAnimalWithAvailableStatus() {
        Animal saved = new Animal();
        saved.setId(1L);
        saved.setName("Rex");
        saved.setType(AnimalType.DOG);
        saved.setStatus(AnimalStatus.AVAILABLE);

        when(animalRepository.save(any(Animal.class))).thenReturn(saved);

        AnimalResponse response = animalService.create(
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Labrador", 3, "Friendly", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Rex");
        assertThat(response.status()).isEqualTo(AnimalStatus.AVAILABLE);
    }

    @Test
    void findById_shouldThrowWhenAnimalNotFound() {
        when(animalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> animalService.findById(99L))
                .isInstanceOf(AnimalNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void adopt_shouldThrowWhenAnimalAlreadyAdopted() {
        Animal adopted = new Animal();
        adopted.setId(1L);
        adopted.setStatus(AnimalStatus.ADOPTED);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(adopted));

        assertThatThrownBy(() -> animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adopt_shouldMarkAnimalAdoptedAndSendNotification() {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setName("Rex");
        animal.setStatus(AnimalStatus.AVAILABLE);

        when(animalRepository.findById(1L)).thenReturn(Optional.of(animal));
        when(animalRepository.save(any(Animal.class))).thenAnswer(returnsFirstArg());

        AnimalResponse response = animalService.adopt(
                new AdoptionRequest(1L, "Anna", "anna@example.com"));

        assertThat(response.status()).isEqualTo(AnimalStatus.ADOPTED);
        verify(notificationClient)
                .sendAdoptionNotification(1L, "Rex", "anna@example.com");
    }

    @Test
    void reserveMultiple_shouldNotifyWithExactlyReservedIds() {
        Animal a1 = animal(1L, AnimalStatus.AVAILABLE);
        Animal a2 = animal(2L, AnimalStatus.AVAILABLE);

        when(animalRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a1, a2));
        when(animalRepository.save(any(Animal.class))).thenAnswer(returnsFirstArg());

        List<AnimalResponse> result = animalService.reserveMultiple(List.of(1L, 2L));

        verify(notificationClient).sendBulkStatusNotification(idsCaptor.capture(), eq("RESERVED"));
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
        assertThat(result).extracting(AnimalResponse::status)
                .containsOnly(AnimalStatus.RESERVED);
    }

    @Test
    void create_shouldRejectDuplicateMicrochipAndNotSave() {
        when(animalRepository.existsByMicrochipId("CHIP-1")).thenReturn(true);

        assertThatThrownBy(() -> animalService.create(
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Lab", 3, "Friendly", "CHIP-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHIP-1");

        verify(animalRepository, never()).save(any());
    }

    private Animal animal(Long id, AnimalStatus status) {
        Animal a = new Animal();
        a.setId(id);
        a.setName("A" + id);
        a.setStatus(status);
        return a;
    }
}
