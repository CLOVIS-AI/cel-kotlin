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

@Suppress("unused")
class TokenizerTest : PreparedSpec({
	identifiers()
})
