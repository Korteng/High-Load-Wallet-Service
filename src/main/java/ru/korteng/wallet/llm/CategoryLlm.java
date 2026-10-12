package ru.korteng.wallet.llm;

import java.util.Optional;

/**
 * Asks a language model to pick a category for an operation description.
 * Kept behind an interface so the provider can be replaced and the classifier tested without a network.
 */
@FunctionalInterface
public interface CategoryLlm {

    Optional<String> suggest(String description);
}
