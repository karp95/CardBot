package com.cardbot.bot;

import com.cardbot.model.Card;
import com.cardbot.model.CardSet;
import com.cardbot.model.User;
import com.cardbot.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CardTelegramBot extends TelegramLongPollingBot {

    private static final int LIST_PAGE_SIZE = 10;

    private static final String CMD_START = "/start";
    private static final String CMD_ADD = "/add";
    private static final String CMD_LEARN = "/learn";
    private static final String CMD_STATS = "/stats";
    private static final String CMD_LIST = "/list";
    private static final String CMD_HELP = "/help";
    private static final String CMD_CANCEL = "/cancel";
    private static final String CMD_SETS = "/sets";

    private static final String CB_SHOW = "SHOW:";
    private static final String CB_NEXT = "NEXT";
    private static final String CB_END = "END";
    private static final String CB_EDIT = "EDIT:";
    private static final String CB_DELETE = "DEL:";
    private static final String CB_DEL_YES = "DELYES:";
    private static final String CB_DEL_NO = "DELNO";
    private static final String CB_LIST = "LIST:";
    private static final String CB_REMIND_LEARN = "REMIND_LEARN";
    private static final String CB_LEARN_SET = "LEARN_SET:";
    private static final String CB_LEARN_MODE = "LEARN_MODE:";
    private static final String CB_LEARN_INPUT = "LEARN_INPUT:";
    private static final String CB_LEARN_INPUT_EXIT = "LEARN_INPUT_EXIT";
    private static final String CB_LEARN_INPUT_SKIP = "LEARN_INPUT_SKIP";
    private static final String CB_LIST_SET = "LIST_SET:";
    private static final String CB_LIST_CHOICE = "LSTCHOICE:";
    private static final Long LEARNING_FILTER_ALL = null;
    private static final Long LEARNING_FILTER_NONE = -1L;

    private static final String CB_MOVE = "MOVE:";
    private static final String CB_MOVE_TO = "MOVETO:";

    private static final String CB_ADD_SET = "ADD_SET";
    private static final String CB_DEL_SET = "DELSET:";
    private static final String CB_DEL_SET_YES = "DELSETYES:";
    private static final String CB_DEL_SET_NO = "DELSETNO";

    private static final String BTN_ADD = "➕ Добавить";
    private static final String BTN_LEARN = "📚 Учить";
    private static final String BTN_LIST = "📋 Список";
    private static final String BTN_STATS = "📊 Статистика";
    private static final String BTN_HELP = "❓ Помощь";
    private static final String BTN_SETS = "📁 Наборы";

    private final UserService userService;
    private final CardService cardService;
    private final CardSetService cardSetService;
    private final LearningService learningService;
    private final StatsService statsService;

    private final Map<Long, UserState> userState = new ConcurrentHashMap<>();
    private final Map<Long, LearningSession> learningSession = new ConcurrentHashMap<>();

    public CardTelegramBot(@Value("${telegram.bot.token}") String botToken,
                          UserService userService,
                          CardService cardService,
                          CardSetService cardSetService,
                          LearningService learningService,
                          StatsService statsService) {
        super(botToken);
        this.userService = userService;
        this.cardService = cardService;
        this.cardSetService = cardSetService;
        this.learningService = learningService;
        this.statsService = statsService;
    }

    @Override
    public String getBotUsername() {
        return "VitaCardsBot";
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки update", e);
            String chatId = update.hasMessage() ? update.getMessage().getChatId().toString()
                    : update.getCallbackQuery().getMessage().getChatId().toString();
            sendText(chatId, "Произошла ошибка. Попробуйте позже.");
        }
    }

    private void handleMessage(Update update) {
        String text = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();
        String username = update.getMessage().getFrom().getUserName();
        Long telegramId = update.getMessage().getFrom().getId();

        User user = userService.getOrCreate(telegramId, username);

        UserState state = userState.get(user.getId());
        if (state != null && state.getType() == UserState.Type.TYPE_LEARN_INPUT && isExitFromInputModeCommand(text)) {
            userState.remove(user.getId());
            learningSession.remove(user.getId());
            if (text.startsWith(CMD_CANCEL)) {
                sendText(chatId.toString(), "Режим «Своё слово» отменён.");
                return;
            }
            // иначе — выходим и обрабатываем команду ниже
        } else if (text.startsWith(CMD_CANCEL)) {
            userState.remove(user.getId());
            sendText(chatId.toString(), "Отменено.");
            return;
        }

        if (text.equals(BTN_SETS) || text.startsWith(CMD_SETS)) {
            handleSets(chatId, user);
            return;
        }

        state = userState.get(user.getId());
        if (state != null && state.getType() == UserState.Type.EDIT_CARD) {
            handleEditInput(chatId, user, text, state.getCardId());
            return;
        }
        if (state != null && state.getType() == UserState.Type.ADD_SET) {
            handleAddSetInput(chatId, user, text);
            return;
        }
        if (state != null && state.getType() == UserState.Type.ADD_CARD) {
            handleAddCardInput(chatId, user, text);
            return;
        }
        if (state != null && state.getType() == UserState.Type.TYPE_LEARN_INPUT) {
            handleLearnInput(chatId, user, text, state.getCardId());
            return;
        }

        String effectiveCommand = mapButtonToCommand(text);
        if (effectiveCommand.startsWith(CMD_START)) {
            handleStart(chatId, update.getMessage().getFrom());
        } else if (effectiveCommand.startsWith(CMD_ADD)) {
            String addInput = text.startsWith(CMD_ADD) ? text.substring(CMD_ADD.length()).trim() : "";
            handleAdd(chatId, user, addInput);
        } else if (effectiveCommand.startsWith(CMD_LEARN)) {
            handleLearn(chatId, user);
        } else if (effectiveCommand.startsWith(CMD_STATS)) {
            handleStats(chatId, user);
        } else if (effectiveCommand.startsWith(CMD_LIST)) {
            showListSetChoice(chatId, user);
        } else if (effectiveCommand.startsWith(CMD_HELP)) {
            handleHelp(chatId, update.getMessage().getFrom());
        } else {
            sendText(chatId.toString(), "Неизвестная команда. Используйте /help для справки.");
        }
    }

    private String mapButtonToCommand(String text) {
        String t = text.trim();
        return switch (t) {
            case BTN_ADD -> CMD_ADD;
            case BTN_LEARN -> CMD_LEARN;
            case BTN_LIST -> CMD_LIST;
            case BTN_STATS -> CMD_STATS;
            case BTN_HELP -> CMD_HELP;
            case BTN_SETS -> CMD_SETS;
            default -> t;
        };
    }

    private ReplyKeyboardMarkup buildMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);
        keyboard.setKeyboard(List.of(
                new KeyboardRow(List.of(
                        KeyboardButton.builder().text(BTN_ADD).build(),
                        KeyboardButton.builder().text(BTN_LEARN).build()
                )),
                new KeyboardRow(List.of(
                        KeyboardButton.builder().text(BTN_LIST).build(),
                        KeyboardButton.builder().text(BTN_STATS).build()
                )),
                new KeyboardRow(List.of(
                        KeyboardButton.builder().text(BTN_SETS).build(),
                        KeyboardButton.builder().text(BTN_HELP).build()
                ))
        ));
        return keyboard;
    }

    private void handleStart(Long chatId, org.telegram.telegrambots.meta.api.objects.User from) {
        String name = (from.getFirstName() != null && !from.getFirstName().isBlank())
                ? from.getFirstName()
                : (from.getUserName() != null ? from.getUserName() : "друг");
        String msg = "Привет, " + name + "! 👋\n\nЯ бот для изучения английских слов.\n\n" +
                "Команды:\n" +
                "/add слово — перевод — добавить карточку\n" +
                "/add набор: слово — перевод — добавить в набор\n" +
                "/learn — начать обучение\n" +
                "/list — список карточек\n" +
                "/sets — управление наборами\n" +
                "/stats — статистика\n" +
                "/cancel — отменить действие\n" +
                "/help — справка";
        SendMessage sendMsg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(msg)
                .replyMarkup(buildMainMenuKeyboard())
                .build();
        try {
            execute(sendMsg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки приветствия", e);
        }
    }

    private void handleAdd(Long chatId, User user, String input) {
        if (input.isEmpty()) {
            userState.put(user.getId(), UserState.addingCard());
            sendText(chatId.toString(), "Введите карточку: слово — перевод\n\n" +
                    "Пример: apple — яблоко\n" +
                    "Пример: животные: dog — собака\n\n" +
                    "Или /cancel для отмены.");
            return;
        }
        addCardFromInput(chatId, user, input);
    }

    private void handleAddCardInput(Long chatId, User user, String text) {
        userState.remove(user.getId());
        addCardFromInput(chatId, user, text.trim());
    }

    private void addCardFromInput(Long chatId, User user, String input) {
        if (input.isEmpty()) {
            sendText(chatId.toString(), "Пустой ввод. Попробуйте снова или /cancel");
            return;
        }
        try {
            Card card = cardService.createFromInput(user, input, cardSetService);
            String setInfo = card.getCardSet() != null ? " (набор «" + card.getCardSet().getName() + "»)" : "";
            sendText(chatId.toString(), "Карточка добавлена" + setInfo + ": " + card.getWord() + " — " + card.getTranslation());
        } catch (IllegalArgumentException e) {
            sendText(chatId.toString(), e.getMessage());
        }
    }

    private void handleLearn(Long chatId, User user) {
        if (cardService.countByUser(user) == 0) {
            sendText(chatId.toString(), "Нет карточек для изучения. Добавьте карточки через /add");
            return;
        }
        showLearnSetChoice(chatId, user);
    }

    private void showLearnSetChoice(Long chatId, User user) {
        var sets = cardSetService.findAllByUser(user);
        long withoutSet = cardService.countByUserWithoutSet(user);

        var keyboard = new java.util.ArrayList<List<InlineKeyboardButton>>();
        keyboard.add(List.of(InlineKeyboardButton.builder().text("📚 Все карточки").callbackData(CB_LEARN_SET + "ALL").build()));
        if (withoutSet > 0) {
            keyboard.add(List.of(InlineKeyboardButton.builder().text("📋 Без набора (" + withoutSet + ")").callbackData(CB_LEARN_SET + "NONE").build()));
        }
        for (var set : sets) {
            long count = cardService.countByUser(user, set.getId());
            if (count > 0) {
                keyboard.add(List.of(InlineKeyboardButton.builder().text("📁 " + set.getName() + " (" + count + ")").callbackData(CB_LEARN_SET + set.getId()).build()));
            }
        }

        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Выберите набор для изучения:")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка", e);
        }
    }

    private void showLearnModeChoice(Long chatId, User user, String setChoice) {
        var keyboard = new java.util.ArrayList<List<InlineKeyboardButton>>();
        keyboard.add(List.of(
                InlineKeyboardButton.builder().text("EN→RU Случайно").callbackData(CB_LEARN_MODE + setChoice + ":EN_RU:RANDOM:").build(),
                InlineKeyboardButton.builder().text("EN→RU По порядку").callbackData(CB_LEARN_MODE + setChoice + ":EN_RU:SEQ:").build()
        ));
        keyboard.add(List.of(
                InlineKeyboardButton.builder().text("RU→EN Случайно").callbackData(CB_LEARN_MODE + setChoice + ":RU_EN:RANDOM:").build(),
                InlineKeyboardButton.builder().text("RU→EN По порядку").callbackData(CB_LEARN_MODE + setChoice + ":RU_EN:SEQ:").build()
        ));
        keyboard.add(List.of(
                InlineKeyboardButton.builder().text("Цель: 10 карточек").callbackData(CB_LEARN_MODE + setChoice + ":EN_RU:RANDOM:10").build()
        ));
        keyboard.add(List.of(
                InlineKeyboardButton.builder().text("✏️ Своё слово (RU→EN)").callbackData(CB_LEARN_INPUT + setChoice + ":RU_EN:").build(),
                InlineKeyboardButton.builder().text("✏️ Своё слово (EN→RU)").callbackData(CB_LEARN_INPUT + setChoice + ":EN_RU:").build()
        ));
        keyboard.add(List.of(
                InlineKeyboardButton.builder().text("✏️ Своё слово (RU→EN) — 10 карт").callbackData(CB_LEARN_INPUT + setChoice + ":RU_EN:10").build(),
                InlineKeyboardButton.builder().text("✏️ Своё слово (EN→RU) — 10 карт").callbackData(CB_LEARN_INPUT + setChoice + ":EN_RU:10").build()
        ));
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Выберите режим:")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка", e);
        }
    }

    private void startLearningWithMode(Long chatId, User user, String setChoice, String directionStr, String orderStr, Integer goal) {
        Long setIdFilter = switch (setChoice) {
            case "ALL" -> LEARNING_FILTER_ALL;
            case "NONE" -> LEARNING_FILTER_NONE;
            default -> Long.parseLong(setChoice);
        };
        LearningSession.Direction direction = "RU_EN".equals(directionStr) ? LearningSession.Direction.RU_EN : LearningSession.Direction.EN_RU;
        LearningSession.Order order = "SEQ".equals(orderStr) ? LearningSession.Order.SEQUENTIAL : LearningSession.Order.RANDOM;

        List<Card> sequentialCards;
        if (setIdFilter == null) {
            sequentialCards = cardService.findAllByUserOrdered(user.getId(), null);
        } else if (LEARNING_FILTER_NONE.equals(setIdFilter)) {
            sequentialCards = cardService.findAllByUserOrdered(user.getId(), -1L);
        } else {
            sequentialCards = cardService.findAllByUserOrdered(user.getId(), setIdFilter);
        }

        if (sequentialCards.isEmpty()) {
            sendText(chatId.toString(), "Нет карточек в выбранном наборе.");
            return;
        }

        LearningSession session = LearningSession.create(setIdFilter, direction, order, sequentialCards, goal);
        learningSession.put(user.getId(), session);

        Card firstCard = order == LearningSession.Order.SEQUENTIAL ? sequentialCards.get(0) : getRandomFromList(sequentialCards);
        sendCardForLearning(chatId.toString(), user, firstCard, direction, goal);
    }

    private void startLearningWithInputMode(Long chatId, User user, String setChoice, LearningSession.Direction direction, Integer goal) {
        Long setIdFilter = switch (setChoice) {
            case "ALL" -> LEARNING_FILTER_ALL;
            case "NONE" -> LEARNING_FILTER_NONE;
            default -> Long.parseLong(setChoice);
        };

        List<Card> cards;
        if (setIdFilter == null) {
            cards = cardService.findAllByUser(user);
        } else if (LEARNING_FILTER_NONE.equals(setIdFilter)) {
            cards = cardService.findAllByUserWithoutSet(user);
        } else {
            cards = cardService.findAllByUser(user, setIdFilter);
        }

        if (cards.isEmpty()) {
            sendText(chatId.toString(), "Нет карточек в выбранном наборе.");
            return;
        }

        LearningSession session = LearningSession.create(setIdFilter, direction, LearningSession.Order.RANDOM, cards, goal);
        learningSession.put(user.getId(), session);

        Card firstCard = getRandomFromList(cards);
        sendCardForInputMode(chatId.toString(), user, firstCard, direction);
    }

    private void sendCardForInputMode(String chatId, User user, Card card, LearningSession.Direction direction) {
        userState.put(user.getId(), UserState.typeLearnInput(card.getId()));
        LearningSession session = learningSession.get(user.getId());
        String question = direction == LearningSession.Direction.RU_EN ? card.getTranslation() : card.getWord();
        String transcription = (direction == LearningSession.Direction.EN_RU && card.getTranscription() != null && !card.getTranscription().isBlank())
                ? "\n_" + card.getTranscription() + "_"
                : "";
        String prompt = direction == LearningSession.Direction.RU_EN ? "Введите слово на английском:" : "Введите перевод на русском:";
        String progress = session != null && session.getGoal() != null
                ? "\n\n(" + (session.getViewedCount() + 1) + "/" + session.getGoal() + ")"
                : "";
        String msg = "*" + question + "*" + transcription + "\n\n" + prompt + progress;
        try {
            execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(msg)
                    .parseMode("Markdown")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(
                                    InlineKeyboardButton.builder().text("⏭ Пропустить").callbackData(CB_LEARN_INPUT_SKIP).build(),
                                    InlineKeyboardButton.builder().text("🚪 Выйти").callbackData(CB_LEARN_INPUT_EXIT).build()))
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки карточки", e);
        }
    }

    private void handleLearnInput(Long chatId, User user, String userInput, Long cardId) {
        Optional<Card> cardOpt = cardService.findByIdAndUserId(cardId, user.getId());
        if (cardOpt.isEmpty()) {
            userState.remove(user.getId());
            sendText(chatId.toString(), "Карточка не найдена.");
            return;
        }
        Card card = cardOpt.get();
        userState.remove(user.getId());

        LearningSession.Direction direction = getLearningDirection(user);
        String expectedRaw = (direction == LearningSession.Direction.RU_EN ? card.getWord() : card.getTranslation()).trim();
        String actual = userInput.trim();
        boolean correct = AnswerChecker.isCorrect(expectedRaw, actual);

        learningService.incrementCardsViewed(user, card);

        LearningSession session = learningSession.get(user.getId());
        if (session != null) {
            session.incrementViewedCount();
        }

        String displayExpected = expectedRaw.split("\\|")[0].trim();
        String resultMsg = correct
                ? "✅ Верно!"
                : "❌ Неверно. Правильно: *" + escapeMarkdown(displayExpected) + "*";
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(resultMsg)
                    .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка", e);
        }

        if (session != null && session.isGoalReached()) {
            learningSession.remove(user.getId());
            sendText(chatId.toString(), "🎉 Сессия завершена! " + session.getViewedCount() + " карточек. Нажмите /learn для новой сессии.");
            return;
        }

        var nextCard = getNextCardForLearning(user);
        if (nextCard.isPresent()) {
            sendCardForInputMode(chatId.toString(), user, nextCard.get(), direction);
        } else {
            learningSession.remove(user.getId());
            sendText(chatId.toString(), "Карточки закончились. Нажмите /learn для новой сессии.");
        }
    }

    private String escapeMarkdown(String s) {
        return s.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[");
    }

    private boolean isExitFromInputModeCommand(String text) {
        String t = text.trim();
        return t.startsWith(CMD_CANCEL) || t.startsWith(CMD_START) || t.startsWith(CMD_ADD)
                || t.startsWith(CMD_LEARN) || t.startsWith(CMD_LIST) || t.startsWith(CMD_STATS)
                || t.startsWith(CMD_HELP) || t.startsWith(CMD_SETS)
                || t.equals(BTN_ADD) || t.equals(BTN_LEARN) || t.equals(BTN_LIST)
                || t.equals(BTN_STATS) || t.equals(BTN_HELP) || t.equals(BTN_SETS);
    }

    private Card getRandomFromList(List<Card> cards) {
        return cards.get((int) (Math.random() * cards.size()));
    }

    private Optional<Card> getNextCardForLearning(User user) {
        LearningSession session = learningSession.get(user.getId());
        if (session == null) {
            return learningService.getNextCard(user);
        }
        if (session.getOrder() == LearningSession.Order.SEQUENTIAL) {
            session.incrementIndex();
            if (session.getSequentialIndex() >= session.getSequentialCards().size()) {
                return Optional.empty();
            }
            return Optional.of(session.getSequentialCards().get(session.getSequentialIndex()));
        }
        Long filter = session.getSetIdFilter();
        if (filter == null) {
            return learningService.getNextCard(user);
        }
        if (LEARNING_FILTER_NONE.equals(filter)) {
            return learningService.getNextCardWithoutSet(user);
        }
        return learningService.getNextCard(user, filter);
    }

    private void sendCardForLearning(String chatId, User user, Card card, LearningSession.Direction direction, Integer goal) {
        LearningSession session = learningSession.get(user.getId());
        String question = direction == LearningSession.Direction.EN_RU ? card.getWord() : card.getTranslation();
        String transcription = (direction == LearningSession.Direction.EN_RU && card.getTranscription() != null && !card.getTranscription().isBlank())
                ? "\n_" + card.getTranscription() + "_"
                : "";
        String showLabel = direction == LearningSession.Direction.EN_RU ? "Показать перевод" : "Показать слово";
        String progress = goal != null && session != null ? "\n\n(" + (session.getViewedCount() + 1) + "/" + goal + ")" : "";

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("*" + question + "*" + transcription + progress);
        msg.setParseMode("Markdown");

        InlineKeyboardButton showBtn = InlineKeyboardButton.builder()
                .text(showLabel)
                .callbackData(CB_SHOW + card.getId())
                .build();
        InlineKeyboardButton nextBtn = InlineKeyboardButton.builder()
                .text("Следующая")
                .callbackData(CB_NEXT)
                .build();
        InlineKeyboardButton endBtn = InlineKeyboardButton.builder()
                .text("Закончить")
                .callbackData(CB_END)
                .build();

        msg.setReplyMarkup(InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(showBtn))
                .keyboardRow(List.of(nextBtn, endBtn))
                .build());

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки карточки", e);
        }
    }

    private void handleCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long telegramId = update.getCallbackQuery().getFrom().getId();

        User user = userService.getOrCreate(telegramId, update.getCallbackQuery().getFrom().getUserName());

        if (data.startsWith(CB_SHOW)) {
            Long cardId = Long.parseLong(data.substring(CB_SHOW.length()));
            cardService.findByIdAndUserId(cardId, user.getId()).ifPresent(card -> {
                learningService.incrementCardsViewed(user, card);
                LearningSession.Direction dir = getLearningDirection(user);
                editToTranslation(chatId, messageId, card, dir);
            });
        } else if (data.startsWith(CB_LEARN_SET)) {
            String setChoice = data.substring(CB_LEARN_SET.length());
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
            showLearnModeChoice(chatId, user, setChoice);
        } else if (data.startsWith(CB_LEARN_MODE)) {
            String rest = data.substring(CB_LEARN_MODE.length());
            String[] parts = rest.split(":");
            String setChoice = parts[0];
            String direction = parts[1];
            String order = parts[2];
            Integer goal = parts.length > 3 && !parts[3].isEmpty() ? Integer.parseInt(parts[3]) : null;
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
            startLearningWithMode(chatId, user, setChoice, direction, order, goal);
        } else if (data.startsWith(CB_LEARN_INPUT)) {
            String rest = data.substring(CB_LEARN_INPUT.length());
            String[] parts = rest.split(":");
            String setChoice = parts[0];
            LearningSession.Direction direction = parts.length > 1 && "EN_RU".equals(parts[1])
                    ? LearningSession.Direction.EN_RU
                    : LearningSession.Direction.RU_EN;
            Integer goal = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : null;
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
            startLearningWithInputMode(chatId, user, setChoice, direction, goal);
        } else if (CB_LEARN_INPUT_EXIT.equals(data)) {
            userState.remove(user.getId());
            learningSession.remove(user.getId());
            try {
                execute(EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .text("Режим «Своё слово» завершён. Нажмите /learn для новой сессии.")
                        .build());
            } catch (TelegramApiException e) {
                log.error("Ошибка", e);
            }
        } else if (CB_LEARN_INPUT_SKIP.equals(data)) {
            UserState state = userState.get(user.getId());
            if (state != null && state.getType() == UserState.Type.TYPE_LEARN_INPUT) {
                Long cardId = state.getCardId();
                userState.remove(user.getId());
                cardService.findByIdAndUserId(cardId, user.getId()).ifPresent(card -> {
                    learningService.incrementCardsViewed(user, card);
                    LearningSession session = learningSession.get(user.getId());
                    if (session != null) {
                        session.incrementViewedCount();
                    }
                    LearningSession.Direction dir = getLearningDirection(user);
                    String correctAnswer = dir == LearningSession.Direction.RU_EN ? card.getWord() : card.getTranslation();
                    try {
                        execute(EditMessageText.builder()
                                .chatId(chatId.toString())
                                .messageId(messageId)
                                .text("⏭ Пропущено. Правильно: *" + escapeMarkdown(correctAnswer) + "*")
                                .parseMode("Markdown")
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("Ошибка", e);
                    }
                    if (session != null && session.isGoalReached()) {
                        learningSession.remove(user.getId());
                        sendText(chatId.toString(), "🎉 Сессия завершена! " + session.getViewedCount() + " карточек. Нажмите /learn для новой сессии.");
                        return;
                    }
                    var nextCard = getNextCardForLearning(user);
                    if (nextCard.isPresent()) {
                        sendCardForInputMode(chatId.toString(), user, nextCard.get(), dir);
                    } else {
                        learningSession.remove(user.getId());
                        sendText(chatId.toString(), "Карточки закончились. Нажмите /learn для новой сессии.");
                    }
                });
            }
        } else if (CB_NEXT.equals(data)) {
            LearningSession session = learningSession.get(user.getId());
            if (session != null) {
                session.incrementViewedCount();
                if (session.isGoalReached()) {
                    learningSession.remove(user.getId());
                    try {
                        execute(EditMessageText.builder()
                                .chatId(chatId.toString())
                                .messageId(messageId)
                                .text("🎉 Сессия завершена! " + session.getViewedCount() + " карточек просмотрено. Нажмите /learn для новой сессии.")
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("Ошибка", e);
                    }
                } else {
                    var nextCard = getNextCardForLearning(user);
                    LearningSession.Direction dir = getLearningDirection(user);
                    if (nextCard.isPresent()) {
                    String question = dir == LearningSession.Direction.EN_RU ? nextCard.get().getWord() : nextCard.get().getTranslation();
                    String showLabel = dir == LearningSession.Direction.EN_RU ? "Показать перевод" : "Показать слово";
                    String progress = session != null && session.getGoal() != null ? "\n\n(" + session.getViewedCount() + "/" + session.getGoal() + ")" : "";
                    try {
                        execute(EditMessageText.builder()
                                .chatId(chatId.toString())
                                .messageId(messageId)
                                .text("*" + question + "*" + progress)
                                .parseMode("Markdown")
                                .replyMarkup(InlineKeyboardMarkup.builder()
                                        .keyboardRow(List.of(InlineKeyboardButton.builder().text(showLabel).callbackData(CB_SHOW + nextCard.get().getId()).build()))
                                        .keyboardRow(List.of(
                                                InlineKeyboardButton.builder().text("Следующая").callbackData(CB_NEXT).build(),
                                                InlineKeyboardButton.builder().text("Закончить").callbackData(CB_END).build()))
                                        .build())
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("Ошибка редактирования", e);
                    }
                    } else {
                        try {
                            execute(EditMessageText.builder()
                                    .chatId(chatId.toString())
                                    .messageId(messageId)
                                    .text("Карточки закончились. Нажмите /learn для новой сессии.")
                                    .build());
                        } catch (TelegramApiException e) {
                            log.error("Ошибка", e);
                        }
                    }
                }
            } else {
                var nextCard = getNextCardForLearning(user);
                LearningSession.Direction dir = getLearningDirection(user);
                if (nextCard.isPresent()) {
                    String question = dir == LearningSession.Direction.EN_RU ? nextCard.get().getWord() : nextCard.get().getTranslation();
                    String showLabel = dir == LearningSession.Direction.EN_RU ? "Показать перевод" : "Показать слово";
                    try {
                        execute(EditMessageText.builder()
                                .chatId(chatId.toString())
                                .messageId(messageId)
                                .text("*" + question + "*")
                                .parseMode("Markdown")
                                .replyMarkup(InlineKeyboardMarkup.builder()
                                        .keyboardRow(List.of(InlineKeyboardButton.builder().text(showLabel).callbackData(CB_SHOW + nextCard.get().getId()).build()))
                                        .keyboardRow(List.of(
                                                InlineKeyboardButton.builder().text("Следующая").callbackData(CB_NEXT).build(),
                                                InlineKeyboardButton.builder().text("Закончить").callbackData(CB_END).build()))
                                        .build())
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("Ошибка редактирования", e);
                    }
                } else {
                    try {
                        execute(EditMessageText.builder()
                                .chatId(chatId.toString())
                                .messageId(messageId)
                                .text("Карточки закончились. Нажмите /learn для новой сессии.")
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("Ошибка", e);
                    }
                }
            }
        } else if (CB_END.equals(data)) {
            learningSession.remove(user.getId());
            try {
                execute(EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .text("Сессия завершена. Нажмите /learn чтобы продолжить.")
                        .build());
            } catch (TelegramApiException e) {
                log.error("Ошибка", e);
            }
        } else if (data.startsWith(CB_LIST_CHOICE)) {
            String choice = data.substring(CB_LIST_CHOICE.length());
            Long setId = switch (choice) {
                case "ALL" -> null;
                case "NONE" -> LEARNING_FILTER_NONE;
                default -> Long.parseLong(choice);
            };
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
            handleList(chatId, user, setId, 0);
        } else if (data.startsWith("LIST_SET:")) {
            String rest = data.substring("LIST_SET:".length());
            String[] parts = rest.split(":");
            Long setId = Long.parseLong(parts[0]);
            int page = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            handleList(chatId, user, setId, page);
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
        } else if (data.startsWith(CB_LIST)) {
            String listData = data.substring(CB_LIST.length());
            String[] parts = listData.split(":");
            Long listSetId = parts.length >= 2 ? Long.parseLong(parts[0]) : null;
            int page = parts.length >= 2 ? Integer.parseInt(parts[1]) : Integer.parseInt(listData);
            handleList(chatId, user, listSetId, page);
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
        } else if (data.startsWith(CB_EDIT)) {
            Long cardId = Long.parseLong(data.substring(CB_EDIT.length()));
            cardService.findByIdAndUserId(cardId, user.getId()).ifPresent(card -> {
                userState.put(user.getId(), UserState.editing(cardId));
                sendText(chatId.toString(), "Редактирование: " + card.getWord() + " — " + card.getTranslation() +
                        "\n\nВведите новое значение: слово — перевод\nИли /cancel для отмены");
            });
        } else if (data.startsWith(CB_MOVE)) {
            Long cardId = Long.parseLong(data.substring(CB_MOVE.length()));
            cardService.findByIdAndUserId(cardId, user.getId()).ifPresent(card -> {
                showMoveSetChoice(chatId, messageId, user, card);
            });
        } else if (data.startsWith(CB_MOVE_TO)) {
            String rest = data.substring(CB_MOVE_TO.length());
            String[] parts = rest.split(":");
            Long cardId = Long.parseLong(parts[0]);
            String targetSet = parts[1];
            cardService.findByIdAndUserId(cardId, user.getId()).ifPresent(card -> {
                CardSet newSet = null;
                if (!"NONE".equals(targetSet)) {
                    Long setId = Long.parseLong(targetSet);
                    newSet = cardSetService.findByIdAndUserId(setId, user.getId()).orElse(null);
                }
                cardService.moveToSet(card, newSet);
                String result = newSet != null ? "Карточка перемещена в набор «" + newSet.getName() + "»" : "Карточка убрана из набора";
                try {
                    execute(EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(messageId)
                            .text(result + ": " + card.getWord() + " — " + card.getTranslation())
                            .build());
                } catch (TelegramApiException e) {
                    log.error("Ошибка", e);
                }
            });
        } else if (data.startsWith(CB_DELETE)) {
            Long cardId = Long.parseLong(data.substring(CB_DELETE.length()));
            cardService.findByIdAndUserId(cardId, user.getId()).ifPresent(card -> {
                try {
                    execute(EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(messageId)
                            .text("Удалить карточку «" + card.getWord() + " — " + card.getTranslation() + "»?")
                            .replyMarkup(InlineKeyboardMarkup.builder()
                                    .keyboardRow(List.of(
                                            InlineKeyboardButton.builder().text("Да, удалить").callbackData(CB_DEL_YES + cardId).build(),
                                            InlineKeyboardButton.builder().text("Нет").callbackData(CB_DEL_NO).build()))
                                    .build())
                            .build());
                } catch (TelegramApiException e) {
                    log.error("Ошибка", e);
                }
            });
        } else if (data.startsWith(CB_DEL_YES)) {
            Long cardId = Long.parseLong(data.substring(CB_DEL_YES.length()));
            cardService.findByIdAndUserId(cardId, user.getId()).ifPresent(card -> {
                cardService.delete(card);
                try {
                    execute(EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(messageId)
                            .text("Карточка удалена.")
                            .build());
                } catch (TelegramApiException e) {
                    log.error("Ошибка", e);
                }
            });
        } else if (CB_REMIND_LEARN.equals(data)) {
            var cardOpt = learningService.getNextCard(user);
            if (cardOpt.isPresent()) {
                try {
                    execute(EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(messageId)
                            .text("*" + cardOpt.get().getWord() + "*")
                            .parseMode("Markdown")
                            .replyMarkup(InlineKeyboardMarkup.builder()
                                    .keyboardRow(List.of(InlineKeyboardButton.builder().text("Показать перевод").callbackData(CB_SHOW + cardOpt.get().getId()).build()))
                                    .keyboardRow(List.of(
                                            InlineKeyboardButton.builder().text("Следующая").callbackData(CB_NEXT).build(),
                                            InlineKeyboardButton.builder().text("Закончить").callbackData(CB_END).build()))
                                    .build())
                            .build());
                } catch (TelegramApiException e) {
                    log.error("Ошибка", e);
                }
            } else {
                try {
                    execute(EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(messageId)
                            .text("Нет карточек для изучения. Добавьте через /add")
                            .build());
                } catch (TelegramApiException e) {
                    log.error("Ошибка", e);
                }
            }
        } else if (CB_DEL_NO.equals(data)) {
            handleList(chatId, user, null, 0);
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
        } else if (CB_ADD_SET.equals(data)) {
            userState.put(user.getId(), UserState.addingSet());
            sendText(chatId.toString(), "Введите название набора (например: Животные, Отпуск). Или /cancel для отмены.");
        } else if (data.startsWith(CB_DEL_SET)) {
            Long setId = Long.parseLong(data.substring(CB_DEL_SET.length()));
            cardSetService.findByIdAndUserId(setId, user.getId()).ifPresent(set -> {
                try {
                    execute(EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(messageId)
                            .text("Удалить набор «" + set.getName() + "»? Карточки останутся без набора.")
                            .replyMarkup(InlineKeyboardMarkup.builder()
                                    .keyboardRow(List.of(
                                            InlineKeyboardButton.builder().text("Да, удалить").callbackData(CB_DEL_SET_YES + setId).build(),
                                            InlineKeyboardButton.builder().text("Нет").callbackData(CB_DEL_SET_NO).build()))
                                    .build())
                            .build());
                } catch (TelegramApiException e) {
                    log.error("Ошибка", e);
                }
            });
        } else if (data.startsWith(CB_DEL_SET_YES)) {
            Long setId = Long.parseLong(data.substring(CB_DEL_SET_YES.length()));
            cardSetService.findByIdAndUserId(setId, user.getId()).ifPresent(set -> {
                cardSetService.delete(set);
                try {
                    execute(EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(messageId)
                            .text("Набор удалён.")
                            .build());
                } catch (TelegramApiException e) {
                    log.error("Ошибка", e);
                }
            });
        } else if (CB_DEL_SET_NO.equals(data)) {
            handleSets(chatId, user);
            try {
                execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .build());
            } catch (TelegramApiException ignored) {}
        }

        try {
            execute(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
                    .callbackQueryId(update.getCallbackQuery().getId())
                    .build());
        } catch (TelegramApiException ignored) {}
    }

    private LearningSession.Direction getLearningDirection(User user) {
        LearningSession session = learningSession.get(user.getId());
        return session != null ? session.getDirection() : LearningSession.Direction.EN_RU;
    }

    private void editToTranslation(Long chatId, Integer messageId, Card card, LearningSession.Direction direction) {
        try {
            String transcription = (card.getTranscription() != null && !card.getTranscription().isBlank())
                    ? " [" + card.getTranscription() + "]"
                    : "";
            String text = direction == LearningSession.Direction.EN_RU
                    ? card.getWord() + transcription + " — " + card.getTranslation()
                    : card.getTranslation() + " — " + card.getWord() + transcription;
            InlineKeyboardButton nextBtn = InlineKeyboardButton.builder().text("Следующая").callbackData(CB_NEXT).build();
            InlineKeyboardButton endBtn = InlineKeyboardButton.builder().text("Закончить").callbackData(CB_END).build();

            execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(nextBtn, endBtn))
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка показа перевода", e);
        }
    }

    private void handleStats(Long chatId, User user) {
        var stats = statsService.getStats(user);
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Статистика\n\n");
        sb.append("Всего карточек: ").append(stats.totalCards()).append("\n");
        sb.append("Просмотрено: ").append(stats.cardsViewedTotal()).append(" раз\n");
        if (stats.totalCards() > 0) {
            double avg = (double) stats.cardsViewedTotal() / stats.totalCards();
            sb.append(String.format("~%.1f просмотров на карточку\n", avg));
        }
        sb.append("\n");
        sb.append("Серия дней: ").append(stats.streak()).append("\n");
        sb.append("Сегодня: ").append(stats.distinctCardsToday()).append(" карточек\n");
        sb.append("За неделю: ").append(stats.distinctCardsThisWeek()).append(" карточек\n");
        if (!stats.topViewedCards().isEmpty()) {
            sb.append("\nТоп по просмотрам:\n");
            for (var card : stats.topViewedCards()) {
                sb.append("• ").append(card.getWord()).append(" — ").append(card.getTranslation()).append("\n");
            }
        }
        sendText(chatId.toString(), sb.toString());
    }

    private void handleSets(Long chatId, User user) {
        var sets = cardSetService.findAllByUser(user);
        long withoutSet = cardService.countByUserWithoutSet(user);

        StringBuilder sb = new StringBuilder("📁 Наборы карточек:\n\n");
        sb.append("• Без набора: ").append(withoutSet).append(" карт.\n");
        for (var set : sets) {
            long count = cardService.countByUser(user, set.getId());
            sb.append("• ").append(set.getName()).append(": ").append(count).append(" карт.\n");
        }

        var keyboard = new java.util.ArrayList<List<InlineKeyboardButton>>();
        keyboard.add(List.of(InlineKeyboardButton.builder().text("➕ Создать набор").callbackData(CB_ADD_SET).build()));
        for (var set : sets) {
            keyboard.add(List.of(
                    InlineKeyboardButton.builder().text("📁 " + set.getName()).callbackData("LIST_SET:" + set.getId()).build(),
                    InlineKeyboardButton.builder().text("🗑 Удалить").callbackData(CB_DEL_SET + set.getId()).build()
            ));
        }

        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(sb.toString())
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка", e);
        }
    }

    private void handleAddSetInput(Long chatId, User user, String text) {
        userState.remove(user.getId());
        String name = text.trim();
        if (name.isEmpty()) {
            sendText(chatId.toString(), "Название не может быть пустым. Попробуйте снова или /cancel");
            return;
        }
        try {
            var set = cardSetService.create(user, name);
            sendText(chatId.toString(), "Набор «" + set.getName() + "» создан.");
        } catch (IllegalArgumentException e) {
            sendText(chatId.toString(), e.getMessage());
        }
    }

    private void showListSetChoice(Long chatId, User user) {
        if (cardService.countByUser(user) == 0) {
            sendText(chatId.toString(), "Нет карточек. Добавьте через /add");
            return;
        }
        var sets = cardSetService.findAllByUser(user);
        long withoutSet = cardService.countByUserWithoutSet(user);

        var keyboard = new java.util.ArrayList<List<InlineKeyboardButton>>();
        keyboard.add(List.of(InlineKeyboardButton.builder().text("📚 Все карточки").callbackData(CB_LIST_CHOICE + "ALL").build()));
        if (withoutSet > 0) {
            keyboard.add(List.of(InlineKeyboardButton.builder().text("📋 Без набора (" + withoutSet + ")").callbackData(CB_LIST_CHOICE + "NONE").build()));
        }
        for (var set : sets) {
            long count = cardService.countByUser(user, set.getId());
            if (count > 0) {
                keyboard.add(List.of(InlineKeyboardButton.builder().text("📁 " + set.getName() + " (" + count + ")").callbackData(CB_LIST_CHOICE + set.getId()).build()));
            }
        }

        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Выберите набор:")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка", e);
        }
    }

    private void handleList(Long chatId, User user, int page) {
        handleList(chatId, user, null, page);
    }

    private void handleList(Long chatId, User user, Long setId, int page) {
        List<Card> cards;
        if (setId == null) {
            cards = cardService.findAllByUser(user);
        } else if (LEARNING_FILTER_NONE.equals(setId)) {
            cards = cardService.findAllByUserWithoutSet(user);
        } else {
            cards = cardService.findAllByUser(user, setId);
        }
        if (cards.isEmpty()) {
            sendText(chatId.toString(), "Нет карточек. Добавьте через /add");
            return;
        }
        int totalPages = (cards.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        page = Math.max(0, Math.min(page, totalPages - 1));
        int from = page * LIST_PAGE_SIZE;
        int to = Math.min(from + LIST_PAGE_SIZE, cards.size());

        StringBuilder sb = new StringBuilder("📋 Ваши карточки (").append(from + 1).append("-").append(to).append(" из ").append(cards.size()).append(")\n\n");
        var pageCards = cards.subList(from, to);

        var keyboard = new java.util.ArrayList<List<InlineKeyboardButton>>();
        int num = from + 1;
        for (Card c : pageCards) {
            String setLabel = c.getCardSet() != null ? " (" + c.getCardSet().getName() + ")" : "";
            sb.append(num).append(". ").append(c.getWord()).append(" — ").append(c.getTranslation()).append(setLabel).append("\n");
            String n = String.valueOf(num);
            keyboard.add(List.of(
                    InlineKeyboardButton.builder().text("✏️ " + n).callbackData(CB_EDIT + c.getId()).build(),
                    InlineKeyboardButton.builder().text("📁 " + n).callbackData(CB_MOVE + c.getId()).build(),
                    InlineKeyboardButton.builder().text("🗑 " + n).callbackData(CB_DELETE + c.getId()).build()
            ));
            num++;
        }

        if (totalPages > 1) {
            var navRow = new java.util.ArrayList<InlineKeyboardButton>();
            String listPrefix = setId != null ? "LIST_SET:" + setId + ":" : CB_LIST;
            if (page > 0) {
                navRow.add(InlineKeyboardButton.builder().text("◀ Назад").callbackData(listPrefix + (page - 1)).build());
            }
            if (page < totalPages - 1) {
                navRow.add(InlineKeyboardButton.builder().text("Вперёд ▶").callbackData(listPrefix + (page + 1)).build());
            }
            if (!navRow.isEmpty()) {
                keyboard.add(navRow);
            }
        }

        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(sb.toString())
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки списка", e);
        }
    }

    private void showMoveSetChoice(Long chatId, Integer messageId, User user, Card card) {
        var sets = cardSetService.findAllByUser(user);
        var keyboard = new java.util.ArrayList<List<InlineKeyboardButton>>();
        keyboard.add(List.of(InlineKeyboardButton.builder().text("📋 Без набора").callbackData(CB_MOVE_TO + card.getId() + ":NONE").build()));
        for (var set : sets) {
            keyboard.add(List.of(InlineKeyboardButton.builder().text("📁 " + set.getName()).callbackData(CB_MOVE_TO + card.getId() + ":" + set.getId()).build()));
        }
        try {
            execute(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text("Переместить «" + card.getWord() + " — " + card.getTranslation() + "» в набор:")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка", e);
        }
    }

    private void handleEditInput(Long chatId, User user, String text, Long cardId) {
        userState.remove(user.getId());
        cardService.findByIdAndUserId(cardId, user.getId()).ifPresentOrElse(card -> {
            try {
                var parsed = cardService.parseCardInput(text);
                if (parsed == null) {
                    sendText(chatId.toString(), "Неверный формат. Используйте: слово — перевод");
                    userState.put(user.getId(), UserState.editing(cardId));
                    return;
                }
                cardService.update(card, parsed.word(), parsed.translation(), parsed.transcription());
                sendText(chatId.toString(), "Карточка обновлена: " + parsed.word() + " — " + parsed.translation());
            } catch (Exception e) {
                sendText(chatId.toString(), "Ошибка: " + e.getMessage());
            }
        }, () -> sendText(chatId.toString(), "Карточка не найдена."));
    }

    private void handleHelp(Long chatId, org.telegram.telegrambots.meta.api.objects.User from) {
        String msg = """
            📖 Справка по боту

            ➕ Добавить — добавить новую карточку.
            Нажмите кнопку, затем введите:
            • слово — перевод (например: apple — яблоко)
            • набор: слово — перевод (например: животные: dog — собака)
            • несколько вариантов: слово — перевод1|перевод2 (например: go — идти|ходить)

            📚 Учить — начать изучение слов.
            Выберите набор, затем режим: EN→RU / RU→EN (случайно или по порядку), цель на сессию или «Своё слово» — показ русского, ввод английского (регистр не учитывается).

            📋 Список — просмотр карточек.
            Выберите набор, чтобы увидеть карточки. Можно редактировать (✏️), перемещать в набор (📁) и удалять (🗑).

            📊 Статистика — общая статистика: сколько карточек и сколько раз вы их просмотрели.

            📁 Наборы — управление наборами карточек.
            Создавайте наборы (например: Животные, Отпуск), добавляйте в них карточки, удаляйте наборы.

            ❓ Помощь — эта справка.

            Отмена — /cancel отменяет текущее действие (добавление, редактирование).
            """;
        SendMessage sendMsg = SendMessage.builder()
                .chatId(chatId.toString())
                .text(msg)
                .replyMarkup(buildMainMenuKeyboard())
                .build();
        try {
            execute(sendMsg);
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки справки", e);
        }
    }

    private void sendText(String chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки сообщения", e);
        }
    }
}
