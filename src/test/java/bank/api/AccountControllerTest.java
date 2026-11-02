package bank.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountControllerTest {

    @Autowired
    private MockMvc mvc;

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void open(String id, String balance) throws Exception {
        postJson("/accounts", "{\"accountId\":\"" + id + "\",\"openingBalance\":" + balance + "}")
                .andExpect(status().isCreated());
    }

    @Nested
    class OpeningAccounts {

        @Test
        void returnsCreatedWithTheNewBalanceAndLocation() throws Exception {
            postJson("/accounts", "{\"accountId\":\"ACC-1\",\"openingBalance\":100.50}")
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/accounts/ACC-1/balance"))
                    .andExpect(jsonPath("$.accountId").value("ACC-1"))
                    .andExpect(jsonPath("$.balance").value(100.50));
        }

        @Test
        void openingBalanceIsOptional() throws Exception {
            postJson("/accounts", "{\"accountId\":\"ACC-1\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.balance").value(0));
        }

        @Test
        void duplicateIdIsAConflict() throws Exception {
            open("ACC-1", "10");

            postJson("/accounts", "{\"accountId\":\"ACC-1\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ACCOUNT_ALREADY_EXISTS"));
        }

        @Test
        void missingOrBlankIdIsABadRequest() throws Exception {
            postJson("/accounts", "{}").andExpect(status().isBadRequest());
            postJson("/accounts", "{\"accountId\":\"  \"}").andExpect(status().isBadRequest());
        }

        @Test
        void negativeOrOverPreciseOpeningBalanceIsABadRequest() throws Exception {
            postJson("/accounts", "{\"accountId\":\"A\",\"openingBalance\":-1}")
                    .andExpect(status().isBadRequest());
            postJson("/accounts", "{\"accountId\":\"B\",\"openingBalance\":1.005}")
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Deposits {

        @Test
        void returnsTheLedgerEntryItCreated() throws Exception {
            open("ACC-1", "50");

            postJson("/accounts/ACC-1/deposits", "{\"amount\":25}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("DEPOSIT"))
                    .andExpect(jsonPath("$.amount").value(25))
                    .andExpect(jsonPath("$.balanceAfter").value(75))
                    .andExpect(jsonPath("$.timestamp").isString());
        }

        @Test
        void zeroMissingAndNegativeAmountsAreBadRequests() throws Exception {
            open("ACC-1", "50");

            postJson("/accounts/ACC-1/deposits", "{\"amount\":0}").andExpect(status().isBadRequest());
            postJson("/accounts/ACC-1/deposits", "{}").andExpect(status().isBadRequest());
            postJson("/accounts/ACC-1/deposits", "{\"amount\":-5}").andExpect(status().isBadRequest());
            postJson("/accounts/ACC-1/deposits", "{\"amount\":10.005}").andExpect(status().isBadRequest());
        }

        @Test
        void amountThatIsNotANumberIsABadRequest() throws Exception {
            open("ACC-1", "50");

            postJson("/accounts/ACC-1/deposits", "{\"amount\":\"abc\"}").andExpect(status().isBadRequest());
        }

        @Test
        void missingOrBrokenBodyIsABadRequest() throws Exception {
            open("ACC-1", "50");

            mvc.perform(post("/accounts/ACC-1/deposits")).andExpect(status().isBadRequest());
            postJson("/accounts/ACC-1/deposits", "{not json").andExpect(status().isBadRequest());
        }

        @Test
        void unknownAccountIsNotFound() throws Exception {
            postJson("/accounts/NOPE/deposits", "{\"amount\":10}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
        }
    }

    @Nested
    class Withdrawals {

        @Test
        void reducesTheBalance() throws Exception {
            open("ACC-1", "100");

            postJson("/accounts/ACC-1/withdrawals", "{\"amount\":30}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$.balanceAfter").value(70));
        }

        @Test
        void overdraftIsUnprocessableAndLeavesTheBalanceAlone() throws Exception {
            open("ACC-1", "100");

            postJson("/accounts/ACC-1/withdrawals", "{\"amount\":100.01}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));

            mvc.perform(get("/accounts/ACC-1/balance")).andExpect(jsonPath("$.balance").value(100));
        }
    }

    @Nested
    class Transfers {

        @Test
        void movesMoneyAndReturnsTheSourceSide() throws Exception {
            open("A", "100");
            open("B", "20");

            postJson("/transfers", "{\"fromAccountId\":\"A\",\"toAccountId\":\"B\",\"amount\":40}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                    .andExpect(jsonPath("$.counterpartyAccountId").value("B"))
                    .andExpect(jsonPath("$.transferId").isString());

            mvc.perform(get("/accounts/A/balance")).andExpect(jsonPath("$.balance").value(60));
            mvc.perform(get("/accounts/B/balance")).andExpect(jsonPath("$.balance").value(60));
        }

        @Test
        void insufficientFundsIsUnprocessable() throws Exception {
            open("A", "10");
            open("B", "0");

            postJson("/transfers", "{\"fromAccountId\":\"A\",\"toAccountId\":\"B\",\"amount\":10.01}")
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void unknownAccountIsNotFoundAndNothingMoves() throws Exception {
            open("A", "100");

            postJson("/transfers", "{\"fromAccountId\":\"A\",\"toAccountId\":\"NOPE\",\"amount\":10}")
                    .andExpect(status().isNotFound());

            mvc.perform(get("/accounts/A/balance")).andExpect(jsonPath("$.balance").value(100));
        }

        @Test
        void transferToTheSameAccountIsABadRequest() throws Exception {
            open("A", "100");

            postJson("/transfers", "{\"fromAccountId\":\"A\",\"toAccountId\":\"A\",\"amount\":10}")
                    .andExpect(status().isBadRequest());
        }

        @Test
        void missingFieldsAreRejected() throws Exception {
            open("A", "100");

            postJson("/transfers", "{\"fromAccountId\":\"A\",\"amount\":10}").andExpect(status().isNotFound());
            postJson("/transfers", "{\"fromAccountId\":\"A\",\"toAccountId\":\"B\"}")
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Queries {

        @Test
        void balanceOfAnUnknownAccountIsNotFound() throws Exception {
            mvc.perform(get("/accounts/NOPE/balance")).andExpect(status().isNotFound());
        }

        @Test
        void historyOfAnUnknownAccountIsNotFound() throws Exception {
            mvc.perform(get("/accounts/NOPE/transactions")).andExpect(status().isNotFound());
        }

        @Test
        void historyIsListedOldestFirst() throws Exception {
            open("ACC-1", "100");
            postJson("/accounts/ACC-1/deposits", "{\"amount\":10}");
            postJson("/accounts/ACC-1/withdrawals", "{\"amount\":5}");

            mvc.perform(get("/accounts/ACC-1/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].type").value("OPENING_BALANCE"))
                    .andExpect(jsonPath("$[1].type").value("DEPOSIT"))
                    .andExpect(jsonPath("$[2].type").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$[2].balanceAfter").value(105));
        }

        @Test
        void accountWithNoActivityHasAnEmptyHistory() throws Exception {
            open("ACC-1", "0");

            mvc.perform(get("/accounts/ACC-1/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
