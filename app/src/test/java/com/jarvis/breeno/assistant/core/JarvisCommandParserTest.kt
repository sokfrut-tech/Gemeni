package com.jarvis.breeno.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisCommandParserTest {
    @Test fun parsesOpenApp() {
        assertEquals(JarvisCommand.OpenApp("Настройки"), JarvisCommandParser.parse("Джарвис, открой Настройки"))
    }

    @Test fun parsesSearch() {
        assertEquals(JarvisCommand.Search("погода завтра"), JarvisCommandParser.parse("найди погода завтра"))
    }

    @Test fun parsesNavigationWithDestination() {
        assertEquals(JarvisCommand.Navigate("Берлин"), JarvisCommandParser.parse("маршрут до Берлин"))
    }

    @Test fun parsesNavigationWithoutDestination() {
        assertEquals(JarvisCommand.Navigate(""), JarvisCommandParser.parse("навигатор"))
    }

    @Test fun parsesDialWithoutAutomaticallyCalling() {
        assertEquals(JarvisCommand.Dial("+491234567"), JarvisCommandParser.parse("позвони +491234567"))
    }

    @Test fun parsesTime() {
        assertEquals(JarvisCommand.TellTime, JarvisCommandParser.parse("который час"))
    }

    @Test fun rejectsEmptyAndUnknownCommands() {
        assertEquals(JarvisCommand.Empty, JarvisCommandParser.parse("  "))
        assertEquals(JarvisCommand.Unsupported("включи реактор"), JarvisCommandParser.parse("включи реактор"))
    }
}
