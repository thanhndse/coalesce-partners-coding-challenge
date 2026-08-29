package com.coalesce.challenge;

import com.coalesce.challenge.alert.AlertManager;
import com.coalesce.challenge.alert.LoggingAlertManager;
import com.coalesce.challenge.engine.PnlEngine;
import com.coalesce.challenge.engine.PositionReplayService;
import com.coalesce.challenge.engine.PriceBook;
import com.coalesce.challenge.handler.EventHandler;
import com.coalesce.challenge.handler.FundingEventHandler;
import com.coalesce.challenge.handler.PriceEventHandler;
import com.coalesce.challenge.handler.TradeEventHandler;
import com.coalesce.challenge.report.PnlReportGenerator;
import com.coalesce.challenge.state.EngineState;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;

/** Google Guice bindings for one application instance. */
public final class ApplicationModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EngineState.class).in(Scopes.SINGLETON);
        bind(PriceBook.class).in(Scopes.SINGLETON);
        bind(PositionReplayService.class).in(Scopes.SINGLETON);
        bind(AlertManager.class).to(LoggingAlertManager.class).in(Scopes.SINGLETON);
        bind(PnlEngine.class).in(Scopes.SINGLETON);
        bind(PnlReportGenerator.class).in(Scopes.SINGLETON);

        Multibinder<EventHandler<?>> eventHandlers = Multibinder.newSetBinder(
            binder(), new TypeLiteral<>() { }
        );
        eventHandlers.addBinding().to(TradeEventHandler.class).in(Scopes.SINGLETON);
        eventHandlers.addBinding().to(FundingEventHandler.class).in(Scopes.SINGLETON);
        eventHandlers.addBinding().to(PriceEventHandler.class).in(Scopes.SINGLETON);
    }
}
