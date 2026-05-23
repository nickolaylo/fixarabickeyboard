class CoinsWallet {
  CoinsWallet._();

  static final CoinsWallet instance = CoinsWallet._();

  static const int dailyFreeCoins = 100;
  static const int rewardedCoins = 50;

  int _coins = dailyFreeCoins;

  int get coins => _coins;
  bool get canSpendFixCoin => _coins > 0;

  void spendFixCoin() {
    if (_coins > 0) _coins--;
  }

  void refillDaily() {
    _coins = dailyFreeCoins;
  }

  void addRewardedCoins() {
    _coins += rewardedCoins;
  }
}
