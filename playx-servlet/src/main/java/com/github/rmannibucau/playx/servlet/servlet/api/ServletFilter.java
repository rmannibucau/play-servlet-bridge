package com.github.rmannibucau.playx.servlet.servlet.api;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import akka.util.ByteString;
import play.http.HttpErrorHandler;
import play.libs.streams.Accumulator;
import play.mvc.BodyParser;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;
import play.mvc.Http;
import play.mvc.Result;

public class ServletFilter extends EssentialFilter {

    @Inject
    private PlayServletContext servletContext;

    @Inject
    private HttpErrorHandler httpErrorHandler;

    @Override
    public EssentialAction apply(final EssentialAction next) {
        return new EssentialAction() {

            @Override
            public Accumulator<ByteString, Result> apply(final Http.RequestHeader requestHeader) {
                return servletContext.findMatchingServlet(requestHeader).map(servlet -> {
                    final long length = requestHeader.getHeaders().get("Content-Length").map(Long::parseLong)
                            .orElse(Long.MAX_VALUE);
                    final BodyParser.Bytes slurper = new BodyParser.Bytes(length, httpErrorHandler);
                    return slurper.apply(requestHeader).mapFuture(
                            resultOrBytes -> resultOrBytes.left.map(CompletableFuture::completedFuture).orElseGet(() -> {
                                return servletContext
                                        .executeInvoke(servlet.getDynamicServlet(), requestHeader,
                                                resultOrBytes.right.get().iterator().asInputStream(), servlet.getServletPath())
                                        .toCompletableFuture();
                            }), servletContext.getDefaultExecutor());
                }).orElseGet(() -> next.apply(requestHeader));
            }
        };
    }
}
