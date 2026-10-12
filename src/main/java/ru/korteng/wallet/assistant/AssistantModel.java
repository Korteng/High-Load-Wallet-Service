package ru.korteng.wallet.assistant;

import java.util.Optional;

/** The two LLM steps of the assistant: choose what to look up, then phrase the answer from the found data. */
public interface AssistantModel {

    /** What the model wants to know. {@code action} is one of balance, spending, history, none. */
    record Plan(String action, Integer days, String category, Integer limit) {
    }

    Plan plan(String question, String today);

    Optional<String> phrase(String question, String dataJson);
}
