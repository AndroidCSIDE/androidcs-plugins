package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Completion item kinds based on LSP specification
 * Represents the type of completion suggestion
 */
enum class CompletionItemKind(val value: Int) {
    TEXT(1),
    METHOD(2),
    FUNCTION(3),
    CONSTRUCTOR(4),
    FIELD(5),
    VARIABLE(6),
    CLASS(7),
    INTERFACE(8),
    MODULE(9),
    PROPERTY(10),
    UNIT(11),
    VALUE(12),
    ENUM(13),
    KEYWORD(14),
    SNIPPET(15),
    COLOR(16),
    FILE(17),
    REFERENCE(18),
    FOLDER(19),
    ENUM_MEMBER(20),
    CONSTANT(21),
    STRUCT(22),
    EVENT(23),
    OPERATOR(24),
    TYPE_PARAMETER(25);

    companion object {
        fun fromValue(value: Int): CompletionItemKind? {
            return values().find { it.value == value }
        }
    }

    /**
     * Get a display icon/symbol for this kind
     */
    fun getIcon(): String {
        return when (this) {
            TEXT -> "T"
            METHOD -> "m"
            FUNCTION -> "ƒ"
            CONSTRUCTOR -> "C"
            FIELD -> "F"
            VARIABLE -> "v"
            CLASS -> "C"
            INTERFACE -> "I"
            MODULE -> "M"
            PROPERTY -> "P"
            UNIT -> "U"
            VALUE -> "V"
            ENUM -> "E"
            KEYWORD -> "K"
            SNIPPET -> "S"
            COLOR -> "◼"
            FILE -> "📄"
            REFERENCE -> "R"
            FOLDER -> "📁"
            ENUM_MEMBER -> "e"
            CONSTANT -> "c"
            STRUCT -> "S"
            EVENT -> "⚡"
            OPERATOR -> "+"
            TYPE_PARAMETER -> "<T>"
        }
    }

    /**
     * Get a description for this kind
     */
    fun getDescription(): String {
        return when (this) {
            TEXT -> "Text"
            METHOD -> "Method"
            FUNCTION -> "Function"
            CONSTRUCTOR -> "Constructor"
            FIELD -> "Field"
            VARIABLE -> "Variable"
            CLASS -> "Class"
            INTERFACE -> "Interface"
            MODULE -> "Module"
            PROPERTY -> "Property"
            UNIT -> "Unit"
            VALUE -> "Value"
            ENUM -> "Enum"
            KEYWORD -> "Keyword"
            SNIPPET -> "Snippet"
            COLOR -> "Color"
            FILE -> "File"
            REFERENCE -> "Reference"
            FOLDER -> "Folder"
            ENUM_MEMBER -> "Enum Member"
            CONSTANT -> "Constant"
            STRUCT -> "Struct"
            EVENT -> "Event"
            OPERATOR -> "Operator"
            TYPE_PARAMETER -> "Type Parameter"
        }
    }
}
