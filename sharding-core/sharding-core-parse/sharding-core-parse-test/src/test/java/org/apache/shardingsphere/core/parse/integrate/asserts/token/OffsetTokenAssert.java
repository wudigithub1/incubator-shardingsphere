/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.core.parse.integrate.asserts.token;

import com.google.common.base.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.parse.integrate.asserts.SQLStatementAssertMessage;
import org.apache.shardingsphere.core.parse.integrate.jaxb.token.ExpectedOffsetToken;
import org.apache.shardingsphere.core.parse.integrate.jaxb.token.ExpectedTokens;
import org.apache.shardingsphere.core.parsing.parser.token.OffsetToken;
import org.apache.shardingsphere.core.parsing.parser.token.SQLToken;
import org.apache.shardingsphere.test.sql.SQLCaseType;

import java.util.Collection;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * Offset token assert.
 *
 * @author zhangliang
 */
@RequiredArgsConstructor
final class OffsetTokenAssert {
    
    private final SQLCaseType sqlCaseType;
    
    private final SQLStatementAssertMessage assertMessage;
    
    void assertOffsetToken(final Collection<SQLToken> actual, final ExpectedTokens expected) {
        Optional<OffsetToken> offsetToken = getOffsetToken(actual);
        if (SQLCaseType.Placeholder == sqlCaseType) {
            assertFalse(assertMessage.getFullAssertMessage("Offset token should not exist: "), offsetToken.isPresent());
            return;
        }
        if (offsetToken.isPresent()) {
            assertOffsetToken(offsetToken.get(), expected.getOffsetToken());
        } else {
            assertNull(assertMessage.getFullAssertMessage("Offset token should not exist: "), expected.getOffsetToken());
        }
    }
    
    private void assertOffsetToken(final OffsetToken actual, final ExpectedOffsetToken expected) {
        assertThat(assertMessage.getFullAssertMessage("Offset token begin position assertion error: "), actual.getStartIndex(), is(expected.getBeginPosition()));
        assertThat(assertMessage.getFullAssertMessage("Offset token offset assertion error: "), actual.getOffset(), is(expected.getOffset()));
    }
    
    private Optional<OffsetToken> getOffsetToken(final Collection<SQLToken> actual) {
        for (SQLToken each : actual) {
            if (each instanceof OffsetToken) {
                return Optional.of((OffsetToken) each);
            }
        }
        return Optional.absent();
    }
}
