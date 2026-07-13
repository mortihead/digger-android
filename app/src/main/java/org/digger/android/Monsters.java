package org.digger.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Появление монстров по таймеру — перенос {@code Monster.initMonsters}/
 * {@code doMonsters} с константами первого уровня ({@code getLevelNumberClampedToTen() == 1}):
 * всего 6 монстров за уровень, не более 3 одновременно на экране, пауза
 * между появлениями 43 кадра, первый монстр — через 10 кадров после старта.
 *
 * <p>Гибель монстра (например, раздавленного падающим мешком) сюда не
 * входит и обрабатывается снаружи через {@link #all()} — этот класс только
 * следит за расписанием появления новых.
 */
final class Monsters {

    private static final int TOTAL_MONSTERS = 6;
    private static final int MAX_ON_SCREEN = 3;
    private static final int SPAWN_GAP = 43;
    private static final int INITIAL_DELAY = 10;

    private final List<Monster> monsters = new ArrayList<>();
    private int spawnedCount;
    private int nextSpawnTime = INITIAL_DELAY;

    void update(LevelField field, GoldBags bags, int diggerX, int diggerY) {
        if (nextSpawnTime > 0) {
            nextSpawnTime--;
        } else if (spawnedCount < TOTAL_MONSTERS && monsters.size() < MAX_ON_SCREEN) {
            monsters.add(new Monster());
            spawnedCount++;
            nextSpawnTime = SPAWN_GAP;
        }
        for (Monster monster : monsters) {
            monster.update(field, bags, diggerX, diggerY);
        }
    }

    /**
     * Убирает всех монстров и заново запускает расписание появления с самого
     * начала — перенос {@code Monster.initMonsters()}, который в оригинале
     * вызывается заново при каждой гибели Digger'а (через {@code initChars()}),
     * а не только при переходе на новый уровень.
     */
    void reset() {
        monsters.clear();
        spawnedCount = 0;
        nextSpawnTime = INITIAL_DELAY;
    }

    void draw(CgaScreen screen) {
        for (Monster monster : monsters) {
            monster.draw(screen);
        }
    }

    List<Monster> all() {
        return monsters;
    }

    /**
     * Все монстры уровня заспавнены и ни одного не осталось в живых —
     * перенос условия {@code Monster.getMonstersLeft() == 0}.
     */
    boolean allDefeated() {
        return spawnedCount >= TOTAL_MONSTERS && monsters.isEmpty();
    }
}
