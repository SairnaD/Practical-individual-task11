package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.AnimalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdoptionRecordRepository extends JpaRepository<AdoptionRecord, Long> {

    @Query("""
            SELECT r FROM AdoptionRecord r
            JOIN r.animal a
            WHERE a.type = :type AND r.adoptedAt >= :since
            """)
    List<AdoptionRecord> findRecentAdoptionsByAnimalType(
            @Param("type") AnimalType type,
            @Param("since") LocalDateTime since);

    List<AdoptionRecord> findByAdopterEmailEndingWithIgnoreCase(String domainSuffix);
}
