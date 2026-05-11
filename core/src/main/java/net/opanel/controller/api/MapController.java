package net.opanel.controller.api;

import io.javalin.http.ContentType;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import net.opanel.OPanel;
import net.opanel.controller.BaseController;

import java.nio.file.Files;
import java.nio.file.Path;

public class MapController extends BaseController {
    public MapController(OPanel plugin) {
        super(plugin);
    }

    public Handler getTile = ctx -> {
        final int x;
        final int z;
        try {
            x = Integer.parseInt(ctx.pathParam("x"));
            z = Integer.parseInt(ctx.pathParam("z"));
        } catch (NumberFormatException e) {
            sendResponse(ctx, HttpStatus.BAD_REQUEST, "Invalid chunk coordinates.");
            return;
        }

        Path tilePath = OPanel.MAP_DATA_PATH.resolve(x +"."+ z +".omap");
        if(!Files.isRegularFile(tilePath)) {
            sendResponse(ctx, HttpStatus.NOT_FOUND, "Tile not found.");
            return;
        }

        sendContent(ctx, tilePath, ContentType.APPLICATION_OCTET_STREAM);
    };
}
