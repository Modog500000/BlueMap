/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.core.map.hires;

import com.flowpowered.math.vector.Vector3i;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.TileMetaConsumer;
import de.bluecolored.bluemap.core.map.hires.blockmodel.BlockStateModelFactory;
import de.bluecolored.bluemap.core.resources.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.World;

public class HiresModelRenderer {

    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final RenderSettings renderSettings;

    public HiresModelRenderer(ResourcePack resourcePack, TextureGallery textureGallery, RenderSettings renderSettings) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.renderSettings = renderSettings;
    }

    public void render(World world, Vector3i modelMin, Vector3i modelMax, HiresTileModel model) {
        render(world, modelMin, modelMax, model, (x, z, c, h, l) -> {});
    }

    public void render(World world, Vector3i modelMin, Vector3i modelMax, HiresTileModel model, TileMetaConsumer tileMetaConsumer) {
        Vector3i min = modelMin.max(renderSettings.getMinPos());
        Vector3i max = modelMax.min(renderSettings.getMaxPos());
        Vector3i modelAnchor = new Vector3i(modelMin.getX(), 0, modelMin.getZ());

        // create new for each tile-render since the factory is not threadsafe
        BlockStateModelFactory modelFactory = new BlockStateModelFactory(resourcePack, textureGallery, renderSettings);

        int maxHeight, minY, maxY;
        double topBlockLight;
        Color columnColor = new Color(), blockColor = new Color();
        BlockNeighborhood<?> block = new BlockNeighborhood<>(resourcePack, renderSettings, world, 0, 0, 0);
        BlockModelView blockModel = new BlockModelView(model);

        int x, y, z;
        for (x = min.getX(); x <= max.getX(); x++){
            for (z = min.getZ(); z <= max.getZ(); z++){

                maxHeight = 0;
                topBlockLight = 0f;

                columnColor.set(0, 0, 0, 1, true);

                if (renderSettings.isInsideRenderBoundaries(x, z)) {
                    minY = Math.max(min.getY(), world.getMinY(x, z));
                    maxY = Math.min(max.getY(), world.getMaxY(x, z));

                    for (y = minY; y <= maxY; y++) {
                        block.set(x, y, z);
                        if (!block.isInsideRenderBounds()) continue;

                        blockModel.initialize();

                        modelFactory.render(block, blockModel, blockColor);

                        //update topBlockLight
                        if (
                                y >= renderSettings.getRemoveCavesBelowY() ||
                                (renderSettings.isCaveDetectionUsesBlockLight() ? block.getBlockLightLevel() : block.getSunLightLevel()) > 0
                        ) {
                            if (blockColor.a > 0) {
                                topBlockLight = Math.floor(topBlockLight * (1 - blockColor.a));
                            }
                            topBlockLight = Math.max(topBlockLight, block.getBlockLightLevel());
                        } else {
                            topBlockLight = 0;
                        }

                        // skip empty blocks
                        if (blockModel.getSize() <= 0) continue;

                        // move block-model to correct position
                        blockModel.translate(x - modelAnchor.getX(), y - modelAnchor.getY(), z - modelAnchor.getZ());

                        //update color and height (only if not 100% translucent)
                        if (blockColor.a > 0) {
                            maxHeight = y;
                            columnColor.overlay(blockColor.premultiplied());
                        }
                    }
                }

                tileMetaConsumer.set(x, z, columnColor, maxHeight, (int) topBlockLight);
            }
        }
    }
}
