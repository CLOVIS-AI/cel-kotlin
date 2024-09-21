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
import io.kotest.assertions.throwables.shouldThrow
import opensavvy.cel.lang.tokens.Token.Null
import opensavvy.prepared.compat.arrow.core.assertRaises
import opensavvy.prepared.compat.arrow.core.failOnRaise
import opensavvy.prepared.runner.kotest.PreparedSpec
import opensavvy.prepared.suite.SuiteDsl

private fun SuiteDsl.identifiers() = suite("Identifiers") {
	val validIdentifiers = listOf(
		"foo",
		"bar",
		"a",
		"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
	)

	suite("Validation") {
		val invalidIdentifiers = listOf(
			"",
			Null.lexeme,
			Token.Bool.lexemeTrue,
			Token.Bool.lexemeFalse,
		) + Token.Keyword.entries.map { it.lexeme }

		for (valid in validIdentifiers) test("The identifier '$valid' is valid") {
			Token.Identifier(valid) // No exception should be thrown
		}

		for (invalid in invalidIdentifiers) test("The identifier '$invalid' is invalid") {
			shouldThrow<IllegalArgumentException> {
				Token.Identifier(invalid)
			}
		}
	}

	suite("Serialized representation") {
		for (valid in validIdentifiers) test("String representation of '$valid'") {
			check(Token.Identifier(valid).serialize() == valid)
		}
	}
}

private fun SuiteDsl.integers() = suite("Integers") {
	suite("Tokenize") {
		val cases: List<Pair<String, Long>> = listOf(
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

		for ((input, expected) in cases) {
			test("'$input' can be read as an integer") {
				check(Tokenizer(input).canRead(Token.Integer))
			}

			test("'$input' should be read as $expected") {
				failOnRaise {
					with(Tokenizer(input)) {
						check(readInteger() == Token.Integer(expected))
					}
				}
			}

			test("'$input' should be smartly read to an integer") {
				check(Tokenizer(input).read() == Token.Integer(expected))
			}
		}

		val failedCases: List<Pair<String, Tokenizer.Failure>> = listOf(
			"" to Tokenizer.Failure.Exhausted(Token.Integer),
			"-" to Tokenizer.Failure.Exhausted(Token.Integer),
			"+" to Tokenizer.Failure.WrongTokenType(Token.Integer),
		)

		for ((input, expected) in failedCases) {
			test("'$input' cannot be read as an integer") {
				check(!Tokenizer(input).canRead(Token.Integer))
			}

			test("'$input' should fail with $expected") {
				assertRaises(expected) {
					with(Tokenizer(input)) {
						readInteger()
					}
				}
			}

			test("'$input' should be not be read to an integer") {
				check(Tokenizer(input).read() !is Token.Integer)
			}
		}
	}
}

@Suppress("unused")
class TokenizerTest : PreparedSpec({
	identifiers()
	integers()
})
