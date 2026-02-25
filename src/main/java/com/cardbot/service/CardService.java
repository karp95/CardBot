package com.cardbot.service;

import com.cardbot.model.Card;
import com.cardbot.model.CardSet;
import com.cardbot.model.User;
import com.cardbot.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CardService {

    private static final String SEPARATOR = "\\s*[-—]\\s*";

    private final CardRepository cardRepository;

    /**
     * Парсит строку формата "слово — перевод" (разделитель: - или —).
     */
    public ParsedCard parseCardInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String[] parts = input.trim().split(SEPARATOR, 2);
        if (parts.length < 2) {
            return null;
        }
        String word = parts[0].trim();
        String translation = parts[1].trim();
        if (word.isEmpty() || translation.isEmpty()) {
            return null;
        }
        return new ParsedCard(word, translation, null);
    }

    @Transactional
    public Card create(User user, String word, String translation, String transcription) {
        return create(user, null, word, translation, transcription);
    }

    @Transactional
    public Card create(User user, CardSet cardSet, String word, String translation, String transcription) {
        Card card = Card.builder()
                .user(user)
                .cardSet(cardSet)
                .word(word)
                .translation(translation)
                .transcription(transcription)
                .build();
        return cardRepository.save(card);
    }

    /**
     * Парсит ввод: "слово — перевод" или "набор: слово — перевод".
     */
    public ParsedCardWithSet parseCardInputWithSet(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        String setName = null;
        String cardPart = trimmed;
        int colonIdx = trimmed.indexOf(':');
        if (colonIdx > 0) {
            String before = trimmed.substring(0, colonIdx).trim();
            String after = trimmed.substring(colonIdx + 1).trim();
            if (!before.isEmpty() && !after.isEmpty()) {
                setName = before;
                cardPart = after;
            }
        }
        ParsedCard parsed = parseCardInput(cardPart);
        if (parsed == null) {
            return null;
        }
        return new ParsedCardWithSet(setName, parsed.word(), parsed.translation(), parsed.transcription());
    }

    @Transactional
    public Card createFromInput(User user, String input) {
        return createFromInput(user, input, null);
    }

    @Transactional
    public Card createFromInput(User user, String input, CardSetService cardSetService) {
        ParsedCardWithSet parsed = parseCardInputWithSet(input);
        if (parsed == null) {
            throw new IllegalArgumentException("Неверный формат. Используйте: слово — перевод\nПример: apple — яблоко");
        }
        CardSet set = null;
        if (parsed.setName() != null && cardSetService != null) {
            set = cardSetService.getOrCreate(user, parsed.setName());
        }
        return create(user, set, parsed.word(), parsed.translation(), parsed.transcription());
    }

    /**
     * Массовая загрузка карточек. Каждая строка — одна карточка.
     * Формат строки: "слово — перевод" или "набор: слово — перевод".
     */
    @Transactional
    public BulkAddResult createBulkFromInput(User user, String input, CardSetService cardSetService) {
        if (input == null || input.isBlank()) {
            return new BulkAddResult(0, 0, List.of());
        }
        String[] lines = input.split("\\r?\\n");
        int added = 0;
        List<String> errors = new java.util.ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                createFromInput(user, line, cardSetService);
                added++;
            } catch (IllegalArgumentException e) {
                errors.add("Строка " + (i + 1) + ": " + line.substring(0, Math.min(50, line.length())) + (line.length() > 50 ? "…" : ""));
            }
        }
        return new BulkAddResult(added, lines.length, errors);
    }

    public record BulkAddResult(int added, int totalLines, List<String> errors) {}

    public record ParsedCardWithSet(String setName, String word, String translation, String transcription) {}

    public Optional<Card> findById(Long id) {
        return cardRepository.findById(id);
    }

    public Optional<Card> findByIdAndUserId(Long id, Long userId) {
        return cardRepository.findById(id)
                .filter(card -> card.getUser().getId().equals(userId));
    }

    public List<Card> findAllByUser(User user) {
        return findAllByUser(user, (Long) null);
    }

    public List<Card> findAllByUser(User user, Long setId) {
        if (setId == null) {
            return cardRepository.findByUserId(user.getId(), Pageable.unpaged()).getContent();
        }
        return cardRepository.findByUserIdAndCardSet_Id(user.getId(), setId, Pageable.unpaged()).getContent();
    }

    public List<Card> findAllByUserWithoutSet(User user) {
        return cardRepository.findByUserIdAndCardSetIsNull(user.getId(), Pageable.unpaged()).getContent();
    }

    public long countByUser(User user) {
        return cardRepository.countByUserId(user.getId());
    }

    public long countByUser(User user, Long setId) {
        if (setId == null) {
            return cardRepository.countByUserId(user.getId());
        }
        return cardRepository.countByUserIdAndCardSet_Id(user.getId(), setId);
    }

    public long countByUserWithoutSet(User user) {
        return cardRepository.countByUserIdAndCardSetIsNull(user.getId());
    }

    public Optional<Card> getRandomCard(User user) {
        return getRandomCard(user, null);
    }

    public Optional<Card> getRandomCard(User user, Long setId) {
        if (setId == null) {
            return cardRepository.findRandomByUserId(user.getId());
        }
        return cardRepository.findRandomByUserIdAndCardSetId(user.getId(), setId);
    }

    public Optional<Card> getRandomCardWithoutSet(User user) {
        return cardRepository.findRandomByUserIdAndCardSetIsNull(user.getId());
    }

    public List<Card> findAllByUserOrdered(Long userId, Long setId) {
        Pageable unpaged = Pageable.unpaged();
        if (setId == null) {
            return cardRepository.findByUserIdOrderByIdAsc(userId, unpaged).getContent();
        }
        if (-1L == setId) {
            return cardRepository.findByUserIdAndCardSetIsNullOrderByIdAsc(userId, unpaged).getContent();
        }
        return cardRepository.findByUserIdAndCardSet_IdOrderByIdAsc(userId, setId, unpaged).getContent();
    }

    @Transactional
    public Card update(Card card, String word, String translation, String transcription) {
        card.setWord(word);
        card.setTranslation(translation);
        card.setTranscription(transcription);
        return cardRepository.save(card);
    }

    @Transactional
    public void delete(Card card) {
        cardRepository.delete(card);
    }

    @Transactional
    public Card moveToSet(Card card, CardSet cardSet) {
        card.setCardSet(cardSet);
        return cardRepository.save(card);
    }

    public record ParsedCard(String word, String translation, String transcription) {}
}
