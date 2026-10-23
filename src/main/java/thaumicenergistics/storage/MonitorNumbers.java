package thaumicenergistics.storage;

/** AE2 rv3 ReadableNumberConverter.toWideReadableForm used by the original monitor.
 * Source: AppliedEnergistics/Applied-Energistics-2, rv3-1.7.10, appeng/util/ReadableNumberConverter.java.
 */
public final class MonitorNumbers {
    private MonitorNumbers(){}
    public static String wide(long amount){
        if(amount<0)throw new IllegalArgumentException("Negative stored amount");
        if(Long.toString(amount).length()<=4)return Long.toString(amount);
        long base=amount;double last=amount;int exponent=-1;
        do{last=base;base/=1000;exponent++;}while(Long.toString(base).length()+1>4);
        var symbols=new java.text.DecimalFormatSymbols();symbols.setDecimalSeparator('.');
        var format=new java.text.DecimalFormat(".#;0.#",symbols);format.setRoundingMode(java.math.RoundingMode.DOWN);
        String suffix=String.valueOf("KMGTPE".charAt(exponent));
        String precise=format.format(last/1000)+suffix;
        return precise.length()<=4?precise:Long.toString(base)+suffix;
    }
}
