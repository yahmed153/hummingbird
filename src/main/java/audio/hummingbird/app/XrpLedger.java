package audio.hummingbird.app;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedInteger;
import okhttp3.HttpUrl;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.client.faucet.FaucetClient;
import org.xrpl.xrpl4j.client.faucet.FundAccountRequest;
import org.xrpl.xrpl4j.crypto.keys.KeyPair;
import org.xrpl.xrpl4j.crypto.keys.PrivateKey;
import org.xrpl.xrpl4j.crypto.keys.Seed;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SingleSignedTransaction;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.client.common.LedgerSpecifier;
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.ledger.LedgerRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.immutables.FluentCompareTo;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.XrpCurrencyAmount;

import java.math.BigDecimal;

@ApplicationScoped
public class XrpLedger {
    private static final HttpUrl rippledUrl = HttpUrl.get("https://s.altnet.rippletest.net:51234/");
    private static final XrplClient xrplClient = new XrplClient(rippledUrl);
    private static Address classicAddress;

    private record XrpParams(KeyPair keyPair, UnsignedInteger sequence) {}
    private static XrpParams createWallet() throws JsonRpcClientErrorException, InterruptedException {
        // Create a KeyPair
        KeyPair randomKeyPair = Seed.ed25519Seed().deriveKeyPair();
        System.out.println("Generated KeyPair: " + randomKeyPair);

        // Get the Classic and X-Addresses from testWallet
        classicAddress = randomKeyPair.publicKey().deriveAddress();
        System.out.println("Classic Address: " + classicAddress);

        // Fund the account using the testnet Faucet
        FaucetClient faucetClient = FaucetClient.construct(HttpUrl.get("https://faucet.altnet.rippletest.net"));
        faucetClient.fundAccount(FundAccountRequest.of(classicAddress));
        System.out.println("Funded the account using the Testnet faucet.");

        // Wait for the Faucet Payment to get validated
        Thread.sleep(4 * 1000);

        // Look up your Account Info
        AccountInfoRequestParams requestParams = AccountInfoRequestParams.builder()
                .account(classicAddress)
                .ledgerSpecifier(LedgerSpecifier.VALIDATED)
                .build();
        AccountInfoResult accountInfoResult = xrplClient.accountInfo(requestParams);
        UnsignedInteger sequence = accountInfoResult.accountData().sequence();

        return new XrpParams(randomKeyPair, sequence);
    }
    public static void sendPayment() throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {

        XrpParams xrpParams = createWallet();
        Thread.sleep(1000);
        // Request current fee information from rippled
        FeeResult feeResult = xrplClient.fee();
        XrpCurrencyAmount openLedgerFee = feeResult.drops().openLedgerFee();

        // Get the latest validated ledger index
        LedgerIndex validatedLedger = xrplClient.ledger(
                        LedgerRequestParams.builder()
                                .ledgerSpecifier(LedgerSpecifier.VALIDATED)
                                .build()
                )
                .ledgerIndex()
                .orElseThrow(() -> new RuntimeException("LedgerIndex not available."));

        // LastLedgerSequence is the current ledger index + 4
        UnsignedInteger lastLedgerSequence = validatedLedger.plus(UnsignedInteger.valueOf(4)).unsignedIntegerValue();

        // Construct a Payment
        Payment payment = Payment.builder()
                .account(classicAddress)
                .amount(XrpCurrencyAmount.ofXrp(BigDecimal.ONE))
                .destination(Address.of("rPT1Sjq2YGrBMTttX4GZHjKu9dyfzbpAYe"))
                .sequence(xrpParams.sequence)
                .fee(openLedgerFee)
                .signingPublicKey(xrpParams.keyPair().publicKey())
                .lastLedgerSequence(lastLedgerSequence)
                .build();
        System.out.println("Constructed Payment: " + payment);

        // Construct a SignatureService to sign the Payment
        SignatureService<PrivateKey> signatureService = new BcSignatureService();
        SingleSignedTransaction<Payment> signedPayment = signatureService.sign(xrpParams.keyPair().privateKey(), payment);
        System.out.println("Signed Payment: " + signedPayment.signedTransaction());

        // Submit the Payment
        SubmitResult<Payment> paymentSubmitResult = xrplClient.submit(signedPayment);
        System.out.println(paymentSubmitResult);

        // Wait for validation
        TransactionResult<Payment> transactionResult = null;

        boolean transactionValidated = false;
        boolean transactionExpired = false;
        while (!transactionValidated && !transactionExpired) {
            Thread.sleep(4 * 1000);
            LedgerIndex latestValidatedLedgerIndex = xrplClient.ledger(
                            LedgerRequestParams.builder()
                                    .ledgerSpecifier(LedgerSpecifier.VALIDATED)
                                    .build()
                    )
                    .ledgerIndex()
                    .orElseThrow(() -> new RuntimeException("Ledger response did not contain a LedgerIndex."));

            transactionResult = xrplClient.transaction(TransactionRequestParams.of(signedPayment.hash()), Payment.class);

            if (transactionResult.validated()) {
                System.out.println("Payment was validated with result code " + transactionResult.metadata().get().transactionResult());
                transactionValidated = true;
            } else {
                boolean lastLedgerSequenceHasPassed = FluentCompareTo.
                        is(latestValidatedLedgerIndex.unsignedIntegerValue())
                        .greaterThan(UnsignedInteger.valueOf(lastLedgerSequence.intValue()));
                if (lastLedgerSequenceHasPassed) {
                    System.out.println("LastLedgerSequence has passed. Last tx response: " +
                            transactionResult);
                    transactionExpired = true;
                } else {
                    System.out.println("Payment not yet validated.");
                }
            }
        }

        // Check transaction results
        System.out.println(transactionResult);
        System.out.println("Explorer link: https://testnet.xrpl.org/transactions/" + signedPayment.hash());
        transactionResult.metadata().ifPresent(metadata -> {
            System.out.println("Result code: " + metadata.transactionResult());

            metadata.deliveredAmount().ifPresent(deliveredAmount ->
                    System.out.println("XRP Delivered: " + ((XrpCurrencyAmount) deliveredAmount).toXrp())
            );
        });
    }
}
