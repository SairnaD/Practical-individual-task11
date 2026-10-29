package lv.bootcamp.shelter;

import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.dto.AnimalCreateRequest;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AnimalRepository;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnimalMicrochipRaceTest {

    private static final String CHIP = "CHIP-RACE";
    private static final CyclicBarrier BARRIER = new CyclicBarrier(2);
    private static final AtomicInteger CHECKS_SEEING_FALSE = new AtomicInteger();

    @TestConfiguration
    static class BarrierConfig {
        @Bean
        static BeanPostProcessor animalRepositoryBarrier() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof AnimalRepository real)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            AnimalRepository.class.getClassLoader(),
                            new Class<?>[]{AnimalRepository.class},
                            (proxy, method, args) -> {
                                Object result;
                                try {
                                    result = method.invoke(real, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                                if (method.getName().equals("existsByMicrochipId")
                                        && CHIP.equals(args[0])) {
                                    if (Boolean.FALSE.equals(result)) {
                                        CHECKS_SEEING_FALSE.incrementAndGet();
                                    }
                                    BARRIER.await(5, TimeUnit.SECONDS);
                                }
                                return result;
                            });
                }
            };
        }
    }

    @Autowired
    private AnimalService animalService;

    @Autowired
    private AnimalRepository animalRepository;

    @MockitoBean
    private NotificationClient notificationClient;

    @BeforeEach
    void reset() {
        BARRIER.reset();
        CHECKS_SEEING_FALSE.set(0);
    }

    @AfterEach
    void cleanUp() {
        animalRepository.deleteAll();
    }

    @Test
    void concurrentCreate_bothPassServiceCheck_butDatabaseConstraintAllowsOnlyOne() throws Exception {
        AnimalCreateRequest request =
                new AnimalCreateRequest("Rex", AnimalType.DOG, "Lab", 3, "Friendly", CHIP);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> animalService.create(request)));
            }

            int successes = 0;
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> f : futures) {
                try {
                    f.get(10, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }

            assertThat(CHECKS_SEEING_FALSE.get())
                    .as("failures: %s", failures)
                    .isEqualTo(2);

            assertThat(successes).isEqualTo(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst()).isInstanceOf(DataIntegrityViolationException.class);

            long rows = animalRepository.findAll().stream()
                    .map(Animal::getMicrochipId)
                    .filter(CHIP::equals)
                    .count();
            assertThat(rows).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}