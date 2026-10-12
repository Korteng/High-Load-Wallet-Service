package ru.korteng.wallet.telegram;

/** Answers free-form questions about the user's own wallet (implemented with an LLM when enabled). */
public interface Assistant {

    String ask(long chatId, String question);
}
