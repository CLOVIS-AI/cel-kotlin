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

@file:OptIn(ExperimentalTraceApi::class)

package opensavvy.cel.lang.tokens

import arrow.core.raise.ExperimentalTraceApi
import opensavvy.prepared.compat.arrow.core.assertRaises
import opensavvy.prepared.compat.arrow.core.failOnRaise
import opensavvy.prepared.runner.testballoon.preparedSuite
import opensavvy.prepared.suite.SuiteDsl

// region Utilities

private inline fun <reified K, reified T : Token<K>> SuiteDsl.validTokensFor(type: TokenType.Leaf<T>, vararg data: Pair<String, K>) {
	for (expected in data.map { it.second }.distinct()) {
		test("'$expected' is a valid value for '$type'") {
			try {
				when (type) {
					Token.Bool.Companion -> Token.Bool(expected as Boolean)
					Token.Bytes.Companion -> Token.Bytes(expected as ByteArray)
					Token.Decimal.Companion -> Token.Decimal(expected as Double)
					Token.Identifier.Companion -> Token.Identifier(expected as String)
					Token.Integer.Companion -> Token.Integer(expected as Long)
					Token.Keyword.Companion -> expected as Token.Keyword
					Token.Null -> check(expected == null)
					Token.Text.Companion -> Token.Text(expected as String)
					Token.UnsignedInteger.Companion -> Token.UnsignedInteger(expected as ULong)
				}
			} catch (e: Throwable) {
				throw AssertionError("Error while attempting to confirm whether '$expected' (${expected::class}) is a valid value for token type $type", e)
			}
		}
	}

	for ((input, expected) in data) {
		test("'$input' can be read as a $type") {
			check(Tokenizer(input).canRead(type))
		}

		test("'$input' should be read as $expected") {
			failOnRaise {
				with(Tokenizer(input)) {
					check(read(type).value == expected)
				}
			}
		}

		test("'$input' should be automatically read to a $type") {
			check(Tokenizer(input).read() != null)
			check(Tokenizer(input).read() is T)
			check(Tokenizer(input).read()?.value == expected)
		}
	}
}

private inline fun <reified T : Token<*>> SuiteDsl.invalidTokensFor(type: TokenType.Leaf<T>, vararg data: Pair<String, Tokenizer.Failure>) {
	for ((input, expected) in data) {
		test("'$input' cannot be read as a $type") {
			check(!Tokenizer(input).canRead(type))
		}

		test("'$input' should fail with '$expected'") {
			assertRaises(expected) {
				with(Tokenizer(input)) {
					read(type)
				}
			}
		}

		test("'$input' should not be automatically read to a $type") {
			check(Tokenizer(input).read() !is T)
		}
	}
}

// endregion

private fun SuiteDsl.identifiers() = suite("Identifiers") {
	val reserved: List<String> = listOf(
		Token.Null.lexeme,
		Token.Bool.lexemeTrue,
		Token.Bool.lexemeFalse,
		*Token.Keyword.all.map { it.lexeme }.toTypedArray(),
	)

	val valid: List<String> = listOf(
		"foo",
		"bar",
		"a",
		"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
		"_",
		"_private",
		"__str__",
		"Arthur",
		"artHur",
		"a1235",
		"_4",
		*reserved.map { "_$it" }.toTypedArray(),
		*reserved.map { it.uppercase() }.toTypedArray(),
	)

	val invalid = listOf(
		"",
		"5",
		"-4",
		*reserved.toTypedArray(),
	)

	validTokensFor(
		Token.Identifier,
		*valid.map { it to it }.toTypedArray(),
	)

	invalidTokensFor(
		Token.Identifier,
		*invalid.map { it to Tokenizer.Failure.WrongTokenType(Token.Identifier) }.toTypedArray(),
	)

	for (ident in invalid) test("The identifier '$ident' is invalid") {
		try {
			Token.Identifier(ident)
			error("Expected to throw IllegalArgumentException")
		} catch (e: IllegalArgumentException) {}
	}

	suite("Serialized representation") {
		for (ident in valid) test("String representation of '$ident'") {
			check(Token.Identifier(ident).serialize() == ident)
		}
	}
}

private fun SuiteDsl.integers() = suite("Integers") {
	validTokensFor(
		Token.Integer,
		"0" to 0,
		"00" to 0,
		"000" to 0,
		"0000" to 0,
		"00000" to 0,
		"000000" to 0,
		"0000000" to 0,
		"00000000" to 0,
		"1" to 1,
		"8" to 8,
		"127" to 127,
		"   12   " to 12,
		"${Long.MAX_VALUE}" to Long.MAX_VALUE,
		"-1" to -1,
		"${Long.MIN_VALUE}" to Long.MIN_VALUE,
		"0x0" to 0,
		"0x1" to 1,
		"0xff" to 0xff,
		"0xFF" to 0xff,
		"0x" + Long.MAX_VALUE.toString(16) to Long.MAX_VALUE,
		"-0x1" to -1,
		"-0xff" to -0xff,
		"-0xFe" to -0xfe,
	)

	invalidTokensFor(
		Token.Integer,
		"" to Tokenizer.Failure.Exhausted(Token.Integer),
		"-" to Tokenizer.Failure.Exhausted(Token.Integer),
		"+" to Tokenizer.Failure.WrongTokenType(Token.Integer)
	)
}

private fun SuiteDsl.decimals() = suite("Decimals") {
	validTokensFor(
		Token.Decimal,
		"0.0" to 0.0,
		"1.0" to 1.0,
		"0.1" to 0.1,
		".2" to 0.2,
		"-0.0" to -0.0,
		"-1.0" to -1.0,
		"-0.1" to -0.1,
		"165678413.1264897" to 165678413.1264897,
		"-126.2678" to -126.2678,
		"1.0e2" to 1.0e2,
		"9.3E4" to 9.3e4,
		"4e78" to 4.0e78,
		"-7.3e4" to -7.3e4,
		"-7.3 e4" to -7.3, // 'e4' is not part of the decimal if there is whitespace
		"7.3e-3" to 7.3e-3,
		"7.3-4" to 7.3, // '-4' is not part of the decimal
		".97e4" to 0.97e4,
		"9.4e+7" to 9.4e7,
	)

	invalidTokensFor(
		Token.Decimal,
		"a" to Tokenizer.Failure.WrongTokenType(Token.Decimal),
		"0.a" to Tokenizer.Failure.WrongTokenType(Token.Decimal),
		"-a" to Tokenizer.Failure.WrongTokenType(Token.Decimal),
		"7.3e" to Tokenizer.Failure.Exhausted(Token.Decimal),
	)
}

private fun SuiteDsl.booleans() = suite("Booleans") {
	validTokensFor(
		Token.Bool,
		"true" to true,
		"false" to false,
		"   true  " to true,
		"       false    " to false,
	)

	invalidTokensFor(
		Token.Bool,
		"'true'" to Tokenizer.Failure.WrongTokenType(Token.Bool),
		"\"true\"" to Tokenizer.Failure.WrongTokenType(Token.Bool),
		"'false'" to Tokenizer.Failure.WrongTokenType(Token.Bool),
		"\"false\"" to Tokenizer.Failure.WrongTokenType(Token.Bool),
		"" to Tokenizer.Failure.WrongTokenType(Token.Bool),
		"0" to Tokenizer.Failure.WrongTokenType(Token.Bool),
		"1" to Tokenizer.Failure.WrongTokenType(Token.Bool),
		"0x0" to Tokenizer.Failure.WrongTokenType(Token.Bool),
	)
}

private fun SuiteDsl.nulls() = suite("Null") {
	test("Null is valid") {
		check(Tokenizer("null").read() == Token.Null)
		check(Tokenizer("    null    ").read() == Token.Null)
	}

	for (invalid in listOf("", "'null'", "\"null\"")) test("'$invalid' is not a valid null representation") {
		check(Tokenizer(invalid).read() != Token.Null)
	}
}

@Suppress("DEPRECATION_ERROR")
private fun SuiteDsl.keywords() = suite("Keywords") {
	val keywords = Token.Keyword.all
		.map { it.lexeme to it }

	validTokensFor(
		Token.Keyword,
		*keywords.toTypedArray(),
	)

	invalidTokensFor(
		Token.Keyword,
		*keywords.map { it.first.uppercase() to Tokenizer.Failure.WrongTokenType(Token.Keyword) }.toTypedArray(),
		Token.Keyword.NonExhaustive.lexeme to Tokenizer.Failure.WrongTokenType(Token.Keyword),
	)
}

@Suppress("unused")
val TokenizerTest by preparedSuite {
	identifiers()
	integers()
	decimals()
	booleans()
	nulls()
	keywords()
}
