package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Symbol kinds based on LSP specification
 * Represents different types of symbols in code (classes, methods, etc.)
 */
enum class SymbolKind(val value: Int) {
    FILE(1),
    MODULE(2),
    NAMESPACE(3),
    PACKAGE(4),
    CLASS(5),
    METHOD(6),
    PROPERTY(7),
    FIELD(8),
    CONSTRUCTOR(9),
    ENUM(10),
    INTERFACE(11),
    FUNCTION(12),
    VARIABLE(13),
    CONSTANT(14),
    STRING(15),
    NUMBER(16),
    BOOLEAN(17),
    ARRAY(18),
    OBJECT(19),
    KEY(20),
    NULL(21),
    ENUM_MEMBER(22),
    STRUCT(23),
    EVENT(24),
    OPERATOR(25),
    TYPE_PARAMETER(26);

    companion object {
        fun fromValue(value: Int): SymbolKind? {
            return values().find { it.value == value }
        }
    }

    /**
     * Get icon for this symbol kind
     */
    fun getIcon(): String {
        return when (this) {
            FILE -> "📄"
            MODULE -> "M"
            NAMESPACE -> "N"
            PACKAGE -> "📦"
            CLASS -> "C"
            METHOD -> "m"
            PROPERTY -> "P"
            FIELD -> "F"
            CONSTRUCTOR -> "⚙"
            ENUM -> "E"
            INTERFACE -> "I"
            FUNCTION -> "ƒ"
            VARIABLE -> "v"
            CONSTANT -> "c"
            STRING -> "\""
            NUMBER -> "#"
            BOOLEAN -> "b"
            ARRAY -> "[]"
            OBJECT -> "{}"
            KEY -> "K"
            NULL -> "∅"
            ENUM_MEMBER -> "e"
            STRUCT -> "S"
            EVENT -> "⚡"
            OPERATOR -> "+"
            TYPE_PARAMETER -> "<T>"
        }
    }
}
