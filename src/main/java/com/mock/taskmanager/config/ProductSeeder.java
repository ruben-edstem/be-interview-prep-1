package com.mock.taskmanager.config;

import com.mock.taskmanager.entity.Product;
import com.mock.taskmanager.repository.ProductRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSeeder implements ApplicationRunner {

    static final int SEED_COUNT = 100;

    private static final long RANDOM_SEED = 42L;
    private static final List<String> CATEGORIES =
            List.of("Electronics", "Books", "Clothing", "Home", "Sports", "Toys");
    private static final List<String> ADJECTIVES =
            List.of("Compact", "Deluxe", "Classic", "Eco", "Smart", "Premium", "Portable", "Rugged");
    private static final List<String> NOUNS =
            List.of("Lamp", "Backpack", "Headphones", "Notebook", "Bottle", "Jacket", "Speaker", "Puzzle");

    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        Random random = new Random(RANDOM_SEED);
        Instant now = Instant.now();
        List<Product> products = IntStream.rangeClosed(1, SEED_COUNT)
                .mapToObj(index -> product(index, random, now))
                .toList();
        productRepository.saveAll(products);
        log.info("Seeded {} products", products.size());
    }

    private Product product(int index, Random random, Instant now) {
        String name = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
                + " " + NOUNS.get(random.nextInt(NOUNS.size())) + " " + index;
        boolean outOfStock = random.nextInt(5) == 0;
        return Product.builder()
                .name(name)
                .category(CATEGORIES.get(random.nextInt(CATEGORIES.size())))
                .price(199L + random.nextInt(49_800))
                .stock(outOfStock ? 0 : 1 + random.nextInt(200))
                .rating(Math.round((1 + random.nextDouble() * 4) * 10) / 10.0)
                .createdAt(now.minus(Duration.ofDays(random.nextInt(365))).minusSeconds(index))
                .build();
    }
}
