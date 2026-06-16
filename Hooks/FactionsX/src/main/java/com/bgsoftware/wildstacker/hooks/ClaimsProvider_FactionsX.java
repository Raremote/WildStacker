package com.bgsoftware.wildstacker.hooks;

import net.prosavage.factionsx.core.FPlayer;
import net.prosavage.factionsx.manager.PlayerManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ClaimsProvider_FactionsX implements ClaimsProvider {

    @Override
    public boolean hasClaimAccess(Player player, Location location) {
        FPlayer me = PlayerManager.INSTANCE.getFPlayer(player);
        return me.getFactionAt().isWilderness() || me.getInBypass() || (me.hasFaction() && me.getFaction().equals(me.getFactionAt()));
    }

}
