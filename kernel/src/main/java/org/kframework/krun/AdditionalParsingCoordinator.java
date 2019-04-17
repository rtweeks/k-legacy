package org.kframework.krun;

import org.kframework.kore.K;
import org.kframework.kore.Sort;

public class AdditionalParsingCoordinator {
  public interface Provider {
    public K apply(String filePath, String module, Sort startSymbol);
  }

  private Provider provider;

  public AdditionalParsingCoordinator() {
    provider = null;
  }

  public void setProvider(Provider provider) {
    this.provider = provider;
  }

  public K parse(String filePath, String module, Sort startSymbol) {
    if (provider == null) {
      throw new RuntimeException("Not implemented!");
    }
    return provider.apply(filePath, module, startSymbol);
  }
}
