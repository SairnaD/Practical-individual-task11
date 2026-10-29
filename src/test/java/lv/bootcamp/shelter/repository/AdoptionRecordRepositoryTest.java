package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    private static final LocalDateTime SINCE = LocalDateTime.of(2025, 1, 1, 0, 0);

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        Animal dog = persistAnimal("Rex", AnimalType.DOG);
        Animal oldDog = persistAnimal("Old", AnimalType.DOG);
        Animal cat = persistAnimal("Mia", AnimalType.CAT);

        persistRecord(dog, "a@example.com", SINCE.plusDays(10));
        persistRecord(oldDog, "b@example.com", SINCE.minusDays(1));
        persistRecord(cat, "c@example.com", SINCE.plusDays(10));
        entityManager.flush();

        var result = adoptionRecordRepository.findRecentAdoptionsByAnimalType(AnimalType.DOG, SINCE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAnimal().getName()).isEqualTo("Rex");
    }

    @Test
    void findRecentAdoptionsByAnimalType_shouldIncludeRecordExactlyAtSince() {
        persistRecord(persistAnimal("Rex", AnimalType.DOG), "a@example.com", SINCE);
        entityManager.flush();

        assertThat(adoptionRecordRepository
                .findRecentAdoptionsByAnimalType(AnimalType.DOG, SINCE)).hasSize(1);
    }

    @Test
    void findByAdopterEmailEndingWith_shouldReturnOnlyMatchingDomain() {
        Animal dog = persistAnimal("Rex", AnimalType.DOG);
        Animal cat = persistAnimal("Mia", AnimalType.CAT);
        Animal bird = persistAnimal("Kiwi", AnimalType.BIRD);

        persistRecord(dog, "anna@example.com", SINCE);
        persistRecord(cat, "ivan@EXAMPLE.com", SINCE);
        persistRecord(bird, "bob@other.org", SINCE);
        entityManager.flush();

        var result = adoptionRecordRepository.findByAdopterEmailEndingWithIgnoreCase("@example.com");

        assertThat(result).extracting(AdoptionRecord::getAdopterEmail)
                .containsExactlyInAnyOrder("anna@example.com", "ivan@EXAMPLE.com");
    }

    private Animal persistAnimal(String name, AnimalType type) {
        Animal a = new Animal();
        a.setName(name);
        a.setType(type);
        a.setStatus(AnimalStatus.ADOPTED);
        return entityManager.persist(a);
    }

    private void persistRecord(Animal animal, String email, LocalDateTime at) {
        AdoptionRecord r = new AdoptionRecord();
        r.setAnimal(animal);
        r.setAdopterEmail(email);
        r.setAdoptedAt(at);
        entityManager.persist(r);
    }
}