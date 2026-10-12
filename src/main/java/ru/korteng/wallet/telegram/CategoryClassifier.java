package ru.korteng.wallet.telegram;

/**
 * Assigns a category to an operation description. The keyword implementation is the default;
 * an LLM-based implementation can replace it later.
 */
public interface CategoryClassifier {

    String classify(String description, boolean income);
}
