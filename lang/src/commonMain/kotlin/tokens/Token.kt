/*
 * Copyright (c) 2024, OpenSavvy and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opensavvy.cel.lang.tokens

import opensavvy.cel.lang.tokens.Token.Keyword.entries
import kotlin.jvm.JvmInline

/**
 * A unique token in a CEL textual representation.
 *
 * Users of this library should be probably not interact with this interface directly, unless performing
 * low-level parsing.
 */
@Suppress("ConstPropertyName")
sealed interface Token<out KotlinType> {

	/**
	 * The value represented by this token, in the Kotlin type system.
	 */
	val value: KotlinType

	/**
	 * Serializes a token into a string representation.
	 *
	 * Tokenizing the returned string is guaranteed to return a [Token] that is
	 * [equal][Any.equals] to the current one.
	 *
	 * The returned string representation is not necessarily identical to the
	 * string representation initially tokenized to obtain this token.
	 * For example, comments are stripped.
	 */
	fun serialize(): String

	@JvmInline
	value class Identifier(override val value: String) : Token<String> {

		init {
			require(pattern.matches(value)) { "Identifier '$value' is not a valid identifier, should validate regex $pattern" }
			require(Keyword.byLexeme(value) == null) { "Identifier '$value' must not be a keyword" }
			require(value != Null.lexeme) { "Identifier '$value' must not be a reserved token" }
			require(value != Bool.lexemeTrue && value != Bool.lexemeFalse) { "Identifier '$value' must not be a reserved token" }
		}

		override fun serialize(): String = value

		companion object : TokenType.Leaf<Identifier> {
			val pattern = Regex("[_a-zA-Z][_a-zA-Z0-9]*")
			override fun toString() = "Token.Identifier"
		}
	}

	sealed interface Literal<out KotlinType> : Token<KotlinType> {

		companion object : TokenType<Literal<*>> {
			override fun toString() = "Token.Literal"
		}
	}

	@JvmInline
	value class Integer(override val value: Long) : Literal<Long> {

		override fun serialize(): String =
			value.toString()

		companion object : TokenType.Leaf<Integer> {
			override fun toString() = "Token.Integer"
		}
	}

	@JvmInline
	value class UnsignedInteger(override val value: ULong) : Literal<ULong> {

		override fun serialize(): String =
			value.toString() + "u"

		companion object : TokenType.Leaf<UnsignedInteger> {
			override fun toString() = "Token.UnsignedInteger"
		}
	}

	@JvmInline
	value class Decimal(override val value: Double) : Literal<Double> {

		override fun serialize(): String =
			value.toString()

		companion object : TokenType.Leaf<Decimal> {
			override fun toString() = "Token.Decimal"
		}
	}

	@JvmInline
	value class Text(override val value: String) : Literal<String> {

		override fun serialize(): String =
			"\"" + value.replace("\n", "\\n") + "\""

		companion object : TokenType.Leaf<Text> {
			override fun toString() = "Token.Text"
		}
	}

	@JvmInline
	value class Bytes(override val value: ByteArray) : Literal<ByteArray> {

		override fun serialize(): String =
			"b$value"

		companion object : TokenType.Leaf<Bytes> {
			override fun toString() = "Token.Bytes"
		}
	}

	sealed interface Reserved<out KotlinType> : Token<KotlinType> {

		companion object : TokenType<Reserved<*>> {
			override fun toString() = "Token.Reserved"
		}
	}

	@JvmInline
	value class Bool(override val value: Boolean) : Reserved<Boolean>, Literal<Boolean> {
		override fun serialize(): String =
			if (value) lexemeTrue else lexemeFalse

		companion object : TokenType.Leaf<Bool> {
			const val lexemeTrue = "true"
			const val lexemeFalse = "false"
			override fun toString() = "Token.Bool"
		}
	}

	data object Null : Reserved<Nothing?>, Literal<Nothing?>, TokenType.Leaf<Null> {
		override val value: Nothing?
			get() = null

		override fun serialize(): String =
			lexeme

		const val lexeme = "null"

		override fun toString() = "Token.Null"
	}

	enum class Keyword(
		val lexeme: String,
	) : Reserved<String> {
		In("in"),
		As("as"),
		Break("break"),
		Const("const"),
		Continue("continue"),
		Else("else"),
		For("for"),
		Function("function"),
		If("if"),
		Import("import"),
		Let("let"),
		Loop("loop"),
		Package("package"),
		Namespace("namespace"),
		Return("return"),
		Var("var"),
		Void("void"),
		While("while"),

		@Deprecated("Elements may be added to this enum in the future, exhaustive whens are forbidden. You must provide an 'else' clause.", level = DeprecationLevel.ERROR)
		NonExhaustive("IMPOSSIBLE VALUE");

		override val value: String
			get() = lexeme

		override fun serialize(): String =
			lexeme

		companion object : TokenType.Leaf<Keyword> {
			private val byLexeme = entries
				.associate { it.lexeme to it }

			fun byLexeme(lexeme: String): Keyword? =
				byLexeme[lexeme]

			override fun toString() = "Token.Keyword"
		}
	}
}

/**
 * Designator for a token type.
 */
sealed interface TokenType<out T : Token<*>> {

	/**
	 * Designator for a leaf token type: a token type that has a specific representation, instead of being
	 * a union of multiple other representations.
	 */
	sealed interface Leaf<out T : Token<*>> : TokenType<T>
}
