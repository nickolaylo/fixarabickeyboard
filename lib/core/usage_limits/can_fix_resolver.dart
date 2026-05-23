import '../coins/coins_wallet.dart';
import '../entitlements/entitlement_store.dart';

class CanFixResolver {
  const CanFixResolver();

  bool canFix() {
    if (EntitlementStore.instance.isPremium) return true;
    return CoinsWallet.instance.canSpendFixCoin;
  }

  void consumeIfNeeded() {
    if (EntitlementStore.instance.isPremium) return;
    CoinsWallet.instance.spendFixCoin();
  }
}
