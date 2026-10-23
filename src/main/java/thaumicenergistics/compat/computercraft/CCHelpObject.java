package thaumicenergistics.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.IDynamicLuaObject;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;

/**
 * Modern CC:Tweaked implementation of the original Thaumic Energistics CCHelpObject.
 * Implements {@link IDynamicLuaObject} and provides {@link LuaFunction} methods for full compatibility.
 */
public final class CCHelpObject implements IDynamicLuaObject {
    public static final CCHelpObject INSTANCE = new CCHelpObject();

    private static final String[] METHOD_NAMES = new String[] {
        "isOnline", "getAspects", "getAmount", "registerAsWatcher", "unregisterAsWatcher", "help", "events"
    };

    private static final String HELP_EVENTS =
        "\nEvent name: essentia\n" +
        "Event Structure: [Index] [Arg1] [Arg2]\n\n" +
        "Essentia Event:[1] [aspectName] [changeAmount]\n" +
        "Power Event:   [2] [true(on)|false(off)]\n" +
        "Detach Event:  [3]\n\n" +
        "Example:\n" +
        "event = { os.pullEvent(\"essentia\") }\n";

    private static final String HELP_IS_ONLINE =
        "\nNo arguments.\n" +
        "Returns true if the provider is powered on and connected, false otherwise.\n\n" +
        "Example:\n" +
        "if( ep.isOnline() ) then\n";

    private static final String HELP_GET_ASPECTS =
        "\nNo arguments.\n" +
        "Returns the name then the amount of each essentia type stored.\n" +
        "Note: You will probably want to wrap the results into a table.\n\n" +
        "Example:\n" +
        "essTable = {ep.getAspects()}\n" +
        "name = essTable[1]\n" +
        "amount = essTable[2]\n";

    private static final String HELP_GET_AMOUNT =
        "\nArguments: AspectName1, AspectName2, ...\n" +
        "Returns the amount(s) of essentia stored in the system for the specified aspect(s).\n\n" +
        "Example:\n" +
        "ordoAmt = ep.getAmount(\"ordo\")\n" +
        "ordoAmt, metoAmt = ep.getAmount(\"ordo\",\"meto\")\n";

    private static final String HELP_REGISTER_AS_WATCHER =
        "\nOptional Arguments: AspectName1, AspectName2, ...\n" +
        "Registers this computer for essentia events. When essentia amounts on the network changes, this computer will receive a notification event.\n" +
        "If aspect arguments are provided, only events for those aspects will be generated. If no arguments are provided, any change will generate an event.\n" +
        "Note: Run help.events() for details about the events.\n\n" +
        "Examples:\n" +
        "ep.registerAsWatcher()\n" +
        "ep.registerAsWatcher(\"ordo\")\n" +
        "ep.registerAsWatcher(\"ordo\",\"meto\")\n";

    private static final String HELP_UNREGISTER_AS_WATCHER =
        "\nOptional Arguments: AspectName1, AspectName2, ...\n" +
        "Unregisters this computer for essentia events.\n" +
        "If aspect arguments are provided, only unregister for those aspects. If no arguments are provided, unregister for all.\n\n" +
        "Examples:\n" +
        "ep.unregisterAsWatcher()\n" +
        "ep.unregisterAsWatcher(\"ordo\")\n" +
        "ep.unregisterAsWatcher(\"meto\",\"ordo\")\n";

    private static final String HELP_HELP =
        "\nDisplays help for the following:\n" +
        String.join("()\n", METHOD_NAMES) + "()\n";

    private CCHelpObject() {}

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES.clone();
    }

    public static String getHelpText(int method) {
        return switch (method) {
            case 0 -> HELP_IS_ONLINE;
            case 1 -> HELP_GET_ASPECTS;
            case 2 -> HELP_GET_AMOUNT;
            case 3 -> HELP_REGISTER_AS_WATCHER;
            case 4 -> HELP_UNREGISTER_AS_WATCHER;
            case 5 -> HELP_HELP;
            case 6 -> HELP_EVENTS;
            default -> null;
        };
    }

    @Override
    public MethodResult callMethod(ILuaContext context, int method, IArguments arguments) throws LuaException {
        return MethodResult.of(getHelpText(method));
    }

    @LuaFunction
    public final String isOnline() { return HELP_IS_ONLINE; }

    @LuaFunction
    public final String getAspects() { return HELP_GET_ASPECTS; }

    @LuaFunction
    public final String getAmount() { return HELP_GET_AMOUNT; }

    @LuaFunction
    public final String registerAsWatcher() { return HELP_REGISTER_AS_WATCHER; }

    @LuaFunction
    public final String unregisterAsWatcher() { return HELP_UNREGISTER_AS_WATCHER; }

    @LuaFunction
    public final String help() { return HELP_HELP; }

    @LuaFunction
    public final String events() { return HELP_EVENTS; }
}
