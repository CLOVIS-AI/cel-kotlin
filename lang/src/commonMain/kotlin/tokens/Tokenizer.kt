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

	fun Raise<Failure>.readBool(): Token.Bool {
		TODO()
	}

	fun Raise<Failure>.readBytes(): Token.Bytes {
		TODO()
	}

	fun Raise<Failure>.readDecimal(): Token.Decimal {
		TODO()
	}

	fun Raise<Failure>.readIdentifier(): Token.Identifier {
		TODO()
	}

	fun Raise<Failure>.readInteger(): Token.Integer {
		TODO()
	}

	fun Raise<Failure>.readKeyword(): Token.Keyword {
		TODO()
	}

	fun Raise<Failure>.readNull(): Token.Null {
		TODO()
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
	fun <T : Token> canRead(type: TokenType.Leaf<T>): Boolean {
		TODO()
	}

	/**
	 * Reads a value of type [type].
	 */
	@Suppress("UNCHECKED_CAST")
	fun <T : Token> Raise<Failure>.read(type: TokenType.Leaf<T>): T =
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
	fun read(): Token? {
		TODO()
	}

	/**
	 * A sequence that reads all tokens in this tokenizer.
	 */
	fun asSequence() = generateSequence { read() }

	interface Failure {

		data class WrongTokenType(
			val expected: TokenType<*>,
			val read: String,
		) : Failure {

			override fun toString() = "Attempted to read token $expected but read '$read'"
		}
	}
}
