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

import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.io.Buffer
import kotlinx.io.Source

/**
 * Helper to convert a textual representation of a CEL expression into its [tokens][Token].
 *
 * Instances of this class are not thread-safe.
 */
class Tokenizer(
	private val source: Source,
) {
	constructor(source: ByteArray) : this(Buffer().apply { write(source) })

	constructor(source: String) : this(source.encodeToByteArray())

	private val whitespaceCharacters = arrayOf(
		' '.code,
		'\t'.code,
		'\n'.code,
		0xC, // '\f'
		'\r'.code,
	)

	private fun skipWhitespace() {
		var whitespaceCounter: Long = 0

		val peek = source.peek()
		while (!peek.exhausted() && peek.readByte().toInt() in whitespaceCharacters) {
			whitespaceCounter++
		}

		if (whitespaceCounter > 0) {
			source.skip(whitespaceCounter)
		}
	}

	private fun continuesWith(text: Char): Boolean =
		source.request(1) && source.peek().readByte().toInt() == text.code

	private fun continuesWith(text: String): Boolean {
		if (!source.request(text.length.toLong()))
			return false

		val source = source.peek()
		for (char in text) {
			if (char.code != source.readByte().toInt())
				return false
		}

		return true
	}

	private fun canReadBool(): Boolean {
		skipWhitespace()
		return continuesWith("true") || continuesWith("false")
	}

	fun Raise<Failure>.readBool(): Token.Bool {
		skipWhitespace()

		return when {
			continuesWith("true") -> {
				source.skip(4)
				Token.Bool(true)
			}

			continuesWith("false") -> {
				source.skip(5)
				Token.Bool(false)
			}

			else -> raise(Failure.WrongTokenType(Token.Bool))
		}
	}

	fun Raise<Failure>.readBytes(): Token.Bytes {
		TODO()
	}

	fun Raise<Failure>.readDecimal(): Token.Decimal {
		TODO()
	}

	private fun canReadIdentifier(): Boolean {
		if (!source.request(1))
			return false

		if (canReadNull() || canReadBool() || canReadKeyword())
			return false

		val next = source.peek().readByte().toInt()
		return next == '_'.code || next in 'a'.code..'z'.code || next in 'A'.code..'Z'.code
	}

	fun Raise<Failure>.readIdentifier(): Token.Identifier {
		skipWhitespace()

		var buffer = StringBuilder()

		var nextIsFirstCharacter = true
		while (source.request(1)) {
			val next = source.peek().readByte().toInt()

			if (next == '_'.code)
				buffer.append('_')
			else if (next in 'a'.code..'z'.code)
				buffer.append(next.toChar())
			else if (next in 'A'.code..'Z'.code)
				buffer.append(next.toChar())
			else if (!nextIsFirstCharacter && next in '0'.code..'9'.code)
				buffer.append(next.toChar())
			else
				break

			nextIsFirstCharacter = false
			source.skip(1)
		}

		if (nextIsFirstCharacter)
			raise(Failure.WrongTokenType(Token.Identifier))

		val identifier = buffer.toString()

		if (identifier == Token.Null.lexeme || identifier == Token.Bool.lexemeTrue || identifier == Token.Bool.lexemeFalse)
			raise(Failure.WrongTokenType(Token.Identifier))

		if (Token.Keyword.byLexeme(identifier) != null)
			raise(Failure.WrongTokenType(Token.Identifier))

		return Token.Identifier(buffer.toString())
	}

	private fun canReadInteger(): Boolean {
		skipWhitespace()
		val source = source.peek()

		if (!source.request(1))
			return false

		// Negative value?
		var next = source.readByte().toInt()
		if (next == '-'.code) {
			if (!source.request(1))
				return false
			next = source.readByte().toInt()
		}

		// Hexadecimal check
		var isHexadecimal = false
		if (source.request(1)) {
			val after = source.readByte().toInt()

			if (next == '0'.code && after == 'x'.code) {
				isHexadecimal = true

				if (!source.request(1))
					return false
				next = source.readByte().toInt()
			}
		}

		return (!isHexadecimal && next in '0'.code..'9'.code) ||
			(isHexadecimal && (next in '0'.code..'9'.code || next in 'a'.code..'f'.code || next in 'A'.code..'F'.code))
	}

	fun Raise<Failure>.readInteger(): Token.Integer {
		skipWhitespace()

		val isNegative = continuesWith('-')
		if (isNegative)
			source.skip(1) // Skip the '-'

		ensure(source.request(1)) { Failure.Exhausted(Token.Integer) }

		val isHexadecimal = continuesWith("0x")
		if (isHexadecimal) {
			source.skip(2)
		}

		var read: Long = 0
		var readAtLeastOne = false

		while (source.request(1)) {
			val next = source.readByte()

			if (!isHexadecimal && next in ('0'.code)..('9'.code)) {
				readAtLeastOne = true
				read *= 10
				read += next - '0'.code
			} else if (isHexadecimal && next in ('0'.code)..('9'.code)) {
				readAtLeastOne = true
				read *= 16
				read += next - '0'.code
			} else if (isHexadecimal && next in ('a'.code)..('f'.code)) {
				readAtLeastOne = true
				read *= 16
				read += next - 'a'.code + 10
			} else if (isHexadecimal && next in ('A'.code)..('F'.code)) {
				readAtLeastOne = true
				read *= 16
				read += next - 'A'.code + 10
			} else {
				break
			}
		}

		ensure(readAtLeastOne) { Failure.WrongTokenType(Token.Integer) }
		return Token.Integer(if (isNegative) -read else read)
	}

	fun canReadKeyword(): Boolean {
		skipWhitespace()

		return Token.Keyword.all.any { continuesWith(it.lexeme) }
	}

	fun Raise<Failure>.readKeyword(): Token.Keyword {
		skipWhitespace()

		for (keyword in Token.Keyword.all) {
			if (continuesWith(keyword.lexeme)) {
				return keyword
			}
		}

		raise(Failure.WrongTokenType(Token.Keyword))
	}

	private fun canReadNull(): Boolean =
		continuesWith("null")

	fun Raise<Failure>.readNull(): Token.Null {
		skipWhitespace()

		if (continuesWith("null")) {
			source.skip(4)
			return Token.Null
		} else {
			raise(Failure.WrongTokenType(Token.Null))
		}
	}

	fun Raise<Failure>.readText(): Token.Text {
		TODO()
	}

	fun Raise<Failure>.readUnsignedInteger(): Token.UnsignedInteger {
		TODO()
	}

	/**
	 * Returns `true` if the next token is of type [type].
	 */
	fun <T : Token<*>> canRead(type: TokenType.Leaf<T>): Boolean {
		return when (type) {
			Token.Bool.Companion -> canReadBool()
			Token.Bytes.Companion -> TODO()
			Token.Decimal.Companion -> TODO()
			Token.Identifier.Companion -> canReadIdentifier()
			Token.Integer.Companion -> canReadInteger()
			Token.Keyword.Companion -> canReadKeyword()
			Token.Null -> canReadNull()
			Token.Text.Companion -> TODO()
			Token.UnsignedInteger.Companion -> TODO()
		}
	}

	/**
	 * Reads a value of type [type].
	 */
	@Suppress("UNCHECKED_CAST")
	fun <T : Token<*>> Raise<Failure>.read(type: TokenType.Leaf<T>): T =
		when (type) {
			Token.Bool.Companion -> readBool() as T
			Token.Bytes.Companion -> readBytes() as T
			Token.Decimal.Companion -> readDecimal() as T
			Token.Identifier.Companion -> readIdentifier() as T
			Token.Integer.Companion -> readInteger() as T
			Token.Keyword.Companion -> readKeyword() as T
			Token.Null -> readNull() as T
			Token.Text.Companion -> readText() as T
			Token.UnsignedInteger.Companion -> readUnsignedInteger() as T
		}

	/**
	 * Reads the next token.
	 *
	 * Returns `null` if the source's end has been reached.
	 */
	fun read(): Token<*>? {
		if (canReadBool())
			return either { readBool() }.getOrNull()

		if (canReadNull())
			return either { readNull() }.getOrNull()

		if (canReadKeyword())
			return either { readKeyword() }.getOrNull()

		if (canReadInteger())
			return either { readInteger() }.getOrNull()

		if (canReadIdentifier())
			return either { readIdentifier() }.getOrNull()

		return null
	}

	/**
	 * A sequence that reads all tokens in this tokenizer.
	 */
	fun asSequence() = generateSequence { read() }

	interface Failure {

		data class WrongTokenType(
			val expected: TokenType<*>,
		) : Failure {

			override fun toString() = "Attempted to read token $expected but read an incompatible value"
		}

		data class Exhausted(
			val expected: TokenType<*>,
		) : Failure {

			override fun toString(): String = "Reached the end of the stream while trying to read a $expected"
		}
	}
}
