package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.*;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    @Mock private AdopterRepository adopterRepository;
    @Mock private AnimalRepository animalRepository;
    @Mock private NotificationClient notificationClient;
    @Mock private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    @Test
    void adopterNotFound_shouldReject() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertRejected(result, RejectionReasons.ADOPTER_NOT_FOUND);
        verifyNoInteractions(animalRepository, notificationClient, auditLogger);
    }

    @Test
    void animalNotFound_shouldReject() {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter(30, 0, false, false)));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        assertRejected(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID), RejectionReasons.ANIMAL_NOT_FOUND);
        verifyNoInteractions(notificationClient, auditLogger);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalStatus.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void animalNotAvailable_shouldReject(AnimalStatus status) {
        stub(adopter(30, 0, false, false), animal(AnimalType.DOG, status, 3));

        assertRejected(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID), RejectionReasons.ANIMAL_NOT_AVAILABLE);
        verifyNoInteractions(notificationClient, auditLogger);
    }

    @Test
    void age17_shouldRejectAsUnderage() {
        stub(adopter(17, 0, false, false), availableDog(3));

        assertRejected(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID), RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void age18_shouldBeAccepted() {
        stub(adopter(18, 0, false, false), availableDog(3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "false, 3",
            "false, 4",
            "true, 5",
            "true, 6"
    })
    void petLimitReached_shouldReject(boolean largeProperty, int petCount) {
        stub(adopter(30, petCount, largeProperty, false), availableDog(3));

        assertRejected(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID), RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.PET_LIMIT_REACHED);
        verifyNoInteractions(notificationClient);
    }

    @ParameterizedTest
    @CsvSource({
            "false, 2",
            "true, 3",
            "true, 4"
    })
    void belowPetLimit_shouldApprove(boolean largeProperty, int petCount) {
        stub(adopter(30, petCount, largeProperty, false), availableDog(3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void exoticWithoutPermit_shouldReject(AnimalType type) {
        stub(adopter(30, 0, false, false), animal(type, AnimalStatus.AVAILABLE, 3));

        assertRejected(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID), RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.NO_EXOTIC_PERMIT);
        verifyNoInteractions(notificationClient);
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"BIRD", "RABBIT"})
    void exoticWithPermit_shouldApprove(AnimalType type) {
        stub(adopter(30, 0, false, true), animal(type, AnimalStatus.AVAILABLE, 3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AnimalType.class, names = {"DOG", "CAT", "OTHER"})
    void nonExoticWithoutPermit_shouldApprove(AnimalType type) {
        stub(adopter(30, 0, false, false), animal(type, AnimalStatus.AVAILABLE, 3));

        assertThat(service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID).approved()).isTrue();
    }

    @Test
    void approved_shouldLogNotifyAndReturnScore() {
        Adopter adopter = adopter(30, 2, true, false);
        adopter.setPreviousAdoptions(4);
        adopter.setEmail("anna@example.com");
        Animal animal = animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 8);
        animal.setName("Rex");
        stub(adopter, animal);

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
        assertThat(result.reason()).isEqualTo("Approved");
        assertThat(result.priorityScore()).isEqualTo(46);
        verify(auditLogger).logApproval(ADOPTER_ID, ANIMAL_ID, 46);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
        verify(auditLogger, never()).logRejection(any(), any(), any());
    }

    @Test
    void score_baseIsZero() {
        assertThat(score(adopter(30, 0, false, false), animalAged(null))).isZero();
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 10", "3, 10", "4, 15"})
    void score_previousAdoptions(int previous, int expected) {
        Adopter a = adopter(30, 0, false, false);
        a.setPreviousAdoptions(previous);
        assertThat(score(a, animalAged(null))).isEqualTo(expected);
    }

    @Test
    void score_largePropertyAdds15() {
        assertThat(score(adopter(30, 0, true, false), animalAged(null))).isEqualTo(15);
    }

    @ParameterizedTest
    @CsvSource(value = {"7, 0", "8, 20", "NULL, 0"}, nullValues = "NULL")
    void score_seniorAnimalBonus(Integer age, int expected) {
        assertThat(score(adopter(30, 0, false, false), animalAged(age))).isEqualTo(expected);
    }

    @Test
    void score_currentPetsSubtract2Each() {
        assertThat(score(adopter(30, 2, true, false), animalAged(null))).isEqualTo(11);
    }

    @Test
    void score_neverNegative() {
        assertThat(score(adopter(30, 3, false, false), animalAged(null))).isZero();
    }

    private int score(Adopter adopter, Animal animal) {
        return service.calculatePriorityScore(adopter, animal);
    }

    private void stub(Adopter adopter, Animal animal) {
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    private void assertRejected(AdoptionResult result, String reason) {
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.priorityScore()).isZero();
    }

    private Adopter adopter(int age, int pets, boolean large, boolean permit) {
        Adopter a = new Adopter();
        a.setAge(age);
        a.setCurrentPetCount(pets);
        a.setLargeProperty(large);
        a.setExoticPermit(permit);
        a.setEmail("test@example.com");
        return a;
    }

    private Animal animal(AnimalType type, AnimalStatus status, Integer age) {
        Animal a = new Animal();
        a.setName("Pet");
        a.setType(type);
        a.setStatus(status);
        a.setAge(age);
        return a;
    }

    private Animal availableDog(Integer age) {
        return animal(AnimalType.DOG, AnimalStatus.AVAILABLE, age);
    }

    private Animal animalAged(Integer age) {
        return availableDog(age);
    }
}