package backlogs.dinamico.tenant;

public final class TenantContextHolder {
  private static final ThreadLocal<TenantContext> TL = new ThreadLocal<>();
  public static void set(TenantContext ctx){ TL.set(ctx); }
  public static TenantContext get(){ return TL.get(); }
  public static void clear(){ TL.remove(); }
}
