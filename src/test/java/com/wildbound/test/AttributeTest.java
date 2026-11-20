package com.wildbound.test;

import com.wildbound.player.AttributeContainer;
import com.wildbound.attributes.DerivedStats;
import com.wildbound.attributes.DerivedStatsCalculator;
import com.wildbound.attributes.RpgAttribute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttributeTest {
    @Test
    void baseAttributesAreFive() {
        assertEquals(5, AttributeContainer.BASE.strength());
        assertEquals(5, AttributeContainer.BASE.spirit());
    }

    @Test
    void increasingAnAttributeDoesNotMutateTheOriginal() {
        AttributeContainer base = AttributeContainer.BASE;
        AttributeContainer changed = base.with(RpgAttribute.STRENGTH, 10);
        assertEquals(5, base.strength());
        assertEquals(10, changed.strength());
    }

    @Test
    void negativeValuesAreClamped() {
        assertEquals(0, AttributeContainer.BASE.with(RpgAttribute.VITALITY, -5).vitality());
    }

    @Test
    void derivedStatsScaleWithAttributes() {
        DerivedStats base = DerivedStatsCalculator.calculate(AttributeContainer.BASE);
        DerivedStats strong = DerivedStatsCalculator.calculate(AttributeContainer.BASE.with(RpgAttribute.STRENGTH, 15));
        assertTrue(strong.meleePower() > base.meleePower());
        assertTrue(strong.maxHealth() == base.maxHealth());
    }

    @Test
    void criticalChanceIsBounded() {
        DerivedStats extreme = DerivedStatsCalculator.calculate(AttributeContainer.BASE.with(RpgAttribute.AGILITY, 100));
        assertTrue(extreme.criticalChance() <= 0.75f);
    }
}
