package dev.pgm.events.ready;

import static tc.oc.pgm.lib.net.kyori.adventure.text.Component.text;

import dev.pgm.events.config.AppData;
import dev.pgm.events.utils.Parties;
import dev.pgm.events.utils.Response;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.events.CountdownCancelEvent;
import tc.oc.pgm.events.CountdownStartEvent;
import tc.oc.pgm.match.ObserverParty;
import tc.oc.pgm.start.StartCountdown;
import tc.oc.pgm.start.StartMatchModule;

public class ReadyManagerImpl implements ReadyManager {

  private final ReadyParties parties;
  private final ReadySystem system;

  public ReadyManagerImpl(ReadySystem system, ReadyParties parties) {
    this.system = system;
    this.parties = parties;
  }

  public void createMatchStart(Match match) {
    createMatchStart(match, Duration.ofSeconds(20));
  }

  public void createMatchStart(Match match, Duration duration) {
    match.needModule(StartMatchModule.class).forceStartCountdown(duration, Duration.ZERO);
  }

  public void cancelMatchStart(Match match) {
    match.getCountdown().cancelAll(StartCountdown.class);
  }

  @Override
  public void ready(Party party) {
    Match match = party.getMatch();

    if (party.isNamePlural()) {
      Bukkit.broadcastMessage(
          party.getColor() + party.getNameLegacy() + ChatColor.RESET + " are now ready.");
    } else {
      Bukkit.broadcastMessage(
          party.getColor() + party.getNameLegacy() + ChatColor.RESET + " is now ready.");
    }

    parties.ready(party);
    if (allReady(match)) {
      createMatchStart(match);
    }
  }

  @Override
  public void unready(Party party) {
    Match match = party.getMatch();

    if (party.isNamePlural()) {
      Bukkit.broadcastMessage(
          party.getColor() + party.getNameLegacy() + ChatColor.RESET + " are now unready.");
    } else {
      Bukkit.broadcastMessage(
          party.getColor() + party.getNameLegacy() + ChatColor.RESET + " is now unready.");
    }

    if (allReady(match)) {
      if (system.unreadyShouldCancel()) {
        // check if unready should cancel
        cancelMatchStart(match);
      }
    }
    parties.unready(party);
  }

  @Override
  public boolean isReady(Party party) {
    return parties.isReady(party);
  }

  @Override
  public boolean allReady(Match match) {
    return parties.allReady(match);
  }

  @Override
  public Response canReady(MatchPlayer player) {
    return canReady(player, true);
  }

  @Override
  public Response canUnready(MatchPlayer player) {
    return canReady(player, false);
  }

  public Response canReady(MatchPlayer player, boolean state) {
    Match match = player.getMatch();
    Party party = player.getParty();

    if (match.isRunning() || match.isFinished()) {
      return Response.deny(text("You are not able use this command during a match!"));
    }

    if (!system.canReady()) {
      return Response.deny(text("You are not able to ready at this time!"));
    }

    if (party instanceof ObserverParty) {
      if (!AppData.observersMustReady()) {
        return Response.deny(text("Observers are not allowed to use this command!"));
      }

      if (!player.getBukkit().hasPermission("events.staff")) {
        return Response.deny(text("You do not have permission to use this command!"));
      }
    }

    if (isReady(party) == state) {
      return Response.deny(text("You are already " + (state ? "ready" : "unready") + "!"));
    }

    if (state && AppData.readyFullTeamRequired() && !Parties.isFull(party)) {
      return Response.deny(text("You can not ready until your team is full!"));
    }

    return Response.allow();
  }

  @Override
  public void reset() {
    parties.reset();
    system.reset();
  }

  @Override
  public void handleCountdownStart(CountdownStartEvent event) {
    Match match = event.getMatch();
    system.onStart(((StartCountdown) event.getCountdown()).getRemaining(), this.allReady(match));
  }

  @Override
  public void handleCountdownCancel(CountdownCancelEvent event) {
    Match match = event.getMatch();
    Duration remaining = system.onCancel(this.allReady(match));
    if (remaining != null) createMatchStart(match, remaining);
  }
}
