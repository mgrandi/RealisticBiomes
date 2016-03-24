package vg.civcraft.mc.realisticbiomes.persist;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.block.Block;

import com.avaje.ebeaninternal.server.lib.sql.DataSourceException;
import vg.civcraft.mc.realisticbiomes.DropGrouper;
import vg.civcraft.mc.realisticbiomes.GrowthConfig;
import vg.civcraft.mc.realisticbiomes.RealisticBiomes;
import vg.civcraft.mc.realisticbiomes.utils.MaterialAliases;

public class PlantChunk {
	private final RealisticBiomes plugin;
	private final ChunkCoords coords;
	// index of this chunk in the database
	private long index;
	
	private HashMap<Coords, Plant> plants;

	boolean loaded;
	boolean inDatabase;

	public PlantChunk(RealisticBiomes plugin, long index, ChunkCoords coords) {
		this.plugin = plugin;
		plants = new HashMap<Coords, Plant>();
		this.index = index;
		this.coords = coords;

		this.loaded = false;
		this.inDatabase = false;
		RealisticBiomes.doLog(Level.FINER,"PlantChunk() called with coords: " + coords);
	}

	/**
	 * tostring override
	 */
	public String toString() {

		StringBuilder sb = new StringBuilder();
		sb.append("<PlantChunk: index: " + index);
		sb.append(" loaded: " + loaded);
		sb.append(" inDatabase: " + inDatabase);
		sb.append(" plants: \n");

		if (this.plants != null) {
			for (Coords iterCoords : plants.keySet()) {

				sb.append("\tPlantHashmapEntry[coords: " + iterCoords);
				sb.append(" = plant: " + plants.get(iterCoords));
				sb.append(" ] \n");

			}
		}

		sb.append(" > ");
		return sb.toString();
	}
	
	
	public ChunkCoords getChunkCoord() {
		return coords;
	}

	// /-------------------------

	public synchronized boolean isLoaded() {
		return loaded;
	}

	public synchronized int getPlantCount() {

		if (plants == null) {
			RealisticBiomes.LOG
					.severe("PLANTS HASHMAP IS NULL, THIS SHOULD NOT HAPPEN");
			return 0;
		}
		return plants.keySet().size();
	}

	// /-------------------------

	public synchronized void remove(Coords coords) {
		if (!loaded)
			return;

		RealisticBiomes.doLog(Level.FINER,"plantchunk.remove(): called with coords: " + coords);
		
		plants.remove(coords);
	}

	public synchronized void addPlant(Coords coords, Plant plant) {

		RealisticBiomes.doLog(Level.FINER,"plantchunk.add(): called with coords: "
				+ coords + " and plant " + plant);
		RealisticBiomes.doLog(Level.FINER, "plantchunk.add(): is loaded? " + loaded);
		if (!loaded) {

			load();

			loaded = true;
		}

		plants.put(coords, plant);
	}

	public synchronized Plant get(Coords coords) {
		if (!loaded) {
			RealisticBiomes.doLog(Level.FINER,"Plantchunk.get(): returning null cause not loaded");
			return null;
		}
		return plants.get(coords);
	}
	
	public synchronized Set<Coords> getPlantCoords() {
		return plants.keySet();
	}
	
	public synchronized boolean load() {
		// wrapper.
		try { 
			return innerLoad();
		} catch (DataSourceException dse) {
			// assume DB has gone away, reconnect and try one more time.
			RealisticBiomes.LOG.log(Level.WARNING, "Looks like DB has gone away: ", dse);			
		}
		
		// if we are here, had failure.
		try {
			plugin.getPlantManager().reconnect();
			return innerLoad();
		} catch(DataSourceException dse) {
			RealisticBiomes.LOG.log(Level.WARNING, "DB really has gone away: ", dse);
			throw dse;
		}
	}

	/**
	 * Loads the plants from the database into this PlantChunk object.
	 * 
	 * @param coords
	 * @return
	 */
	private boolean innerLoad() {
		// if the data is being loaded, it is known that this chunk is in the
		// database

		RealisticBiomes.doLog(Level.FINER, 
				"Plantchunk.load() called with coords: " + coords);

		if (loaded) {
			return true;
		}

		World world = plugin.getServer().getWorld(WorldID.getMCID(coords.w));

		DropGrouper dropGrouper = new DropGrouper(world);

		// execute the load plant statement
		try {

			ChunkWriter.loadPlantsStmt.setLong(1, index);
			RealisticBiomes.doLog(Level.FINER,
					"PlantChunk.load() executing sql query: "
							+ ChunkWriter.loadPlantsStmt.toString());
			ChunkWriter.loadPlantsStmt.execute();

			ResultSet rs = ChunkWriter.loadPlantsStmt.getResultSet();
			while (rs.next()) {
				int w = rs.getInt("w");
				int x = rs.getInt("x");
				int y = rs.getInt("y");
				int z = rs.getInt("z");
				long date = rs.getLong(5);
				float growth = rs.getFloat(6);
				float fruitGrowth = rs.getFloat(7);

				RealisticBiomes.doLog(Level.FINEST, String
								.format("PlantChunk.load(): got result: w:%s x:%s y:%s z:%s date:%s growth:%s",
										w, x, y, z, date, growth));

				// if the plant does not correspond to an actual crop, don't
				// load it
				if (MaterialAliases.getConfig(plugin.materialGrowth, world.getBlockAt(x, y, z)) == null) {
					RealisticBiomes.doLog(Level.FINER, "Plantchunk.load(): plant we got from db doesn't correspond to an actual crop, not loading");
					continue;
				}

				Plant plant = new Plant(date, growth, fruitGrowth);

				Block block = world.getBlockAt(x, y, z);
				GrowthConfig growthConfig = MaterialAliases.getConfig(plugin.materialGrowth, block);
				if (growthConfig.isPersistent()) {
					plugin.growPlant(plant, block, growthConfig, null, dropGrouper);
				}

				// if the plant isn't finished growing, add it to the
				// plants
				if (!plant.isFullyGrown()) {
					plants.put(new Coords(w, x, y, z), plant);
					RealisticBiomes.doLog(Level.FINER, "PlantChunk.load(): plant not finished growing, adding to plants list");
				}
			}
		} catch (SQLException e) {
			throw new DataSourceException(
					String.format(
							"Failed to execute/load the data from the plants table (In PlantChunk.load()) with chunkId %s, coords %s",
							index, coords), e);
		}

		dropGrouper.done();
		
		// TODO: this always returns true...refactor that!
		loaded = true;
		return true;
	}

	public synchronized void unload() {
		// wrapper.
		try { 
			innerUnload();
			return;
		} catch (DataSourceException dse) {
			// assume DB has gone away, reconnect and try one more time.
			RealisticBiomes.LOG.log(Level.WARNING, "Looks like DB has gone away: ", dse);			
		}
		
		// if we are here, had failure.
		try {
			plugin.getPlantManager().reconnect();
			innerUnload();
		} catch(DataSourceException dse) {
			RealisticBiomes.LOG.log(Level.WARNING, "DB really has gone away: ", dse);
			throw dse;
		}
	}

	
	/**
	 * unloads the plant chunk, and saves it to the database.
	 * 
	 * Note that this is called by PlantManager.saveAllAndStop(), so that method
	 * takes care of setting autocommit to false/true and actually committing to
	 * the database

	 * @param writeStmts
	 */
	private void innerUnload() {

		RealisticBiomes.doLog(Level.FINEST,"PlantChunk.unload(): called with coords "
				+ coords + "plantchunk object: " + this);
		
		if (!loaded) {
			RealisticBiomes.doLog(Level.FINEST, "Plantchunk.unload(): not loaded so returning");
			return;
		}

		try {
			// if this chunk was not in the database, then add it to the
			// database
			RealisticBiomes.doLog(Level.FINEST,"PlantChunk.unload(): is inDatabase?: "
					+ inDatabase);
			if (!inDatabase) {

				RealisticBiomes.doLog(Level.FINEST, "not in database, adding new chunk");
				ChunkWriter.addChunkStmt.setInt(1, coords.w);
				ChunkWriter.addChunkStmt.setInt(2, coords.x);
				ChunkWriter.addChunkStmt.setInt(3, coords.z);
				ChunkWriter.addChunkStmt.execute();
				ChunkWriter.getLastChunkIdStmt.execute();
				ResultSet rs = ChunkWriter.getLastChunkIdStmt.getResultSet();

				// need to call rs.next() to get the first result, and make sure
				// we get the index, and throw an exception
				// if we don't
				if (rs.next()) {
					index = rs.getLong(1);
					RealisticBiomes.doLog(Level.FINEST, "plantchunk.unload(): got new autoincrement index, it is now "
									+ index);
				} else {
					throw new DataSourceException(
							"Trying to add the chunk to the database, but was unable to get "
									+ "the last inserted statement to get the index");
				}

				// make sure to commit this newly added chunk, or else when we
				// add plants to the
				// database later in this method we dont get a Constraint
				// Failure exception

				try {
					RealisticBiomes.doLog(Level.FINEST, "plantchunk.unload(): committing new plantchunk with index "
								+ this.index);
					plugin.getPlantManager().getWriteConnection().commit();
				} catch(SQLException e) {
					RealisticBiomes.LOG.warning("Can't commit?" + e);
				}
				inDatabase = true;
			}

		} catch (SQLException e) {

			throw new DataSourceException(
					String.format(
							"Failed to unload the chunk (In PlantChunk, adding chunk to db if needed), index %s, coords %s, PlantChunk obj: %s",
							index, coords, this), e);
		}

		try {
			// put all the plants into the database
			// if we are already unloaded then don't do anything
			if (loaded) {
				if (!plants.isEmpty()) {
					try {
						plugin.getPlantManager().getWriteConnection().setAutoCommit(false);
					} catch (SQLException e) {
						RealisticBiomes.LOG.severe("Can't set autocommit?" + e);
					}

					// delete plants in the database for this chunk and re-add them
					// this is OK because rb_plant does not have a autoincrement index
					// so it won't explode. However, does this have a negative performance impact?
					// TODO: add listener for block break event, and if its a plant, we remove it
					// from the correct plantchunk? Right now if a plant gets destroyed before
					// it is fully grown then it won't get remove from the database
					ChunkWriter.deleteOldPlantsStmt.setLong(1, index);
					ChunkWriter.deleteOldPlantsStmt.execute();

					int coordCounter = 0;
					boolean needToExec = false;
					
					RealisticBiomes.doLog(Level.FINEST, "PlantChunk.unload(): Unloading plantchunk with index: " + this.index);
					for (Coords coords : plants.keySet()) {
						if (!needToExec) {
							needToExec = true;
						}
						
						Plant plant = plants.get(coords);

						ChunkWriter.addPlantStmt.clearParameters();
						ChunkWriter.addPlantStmt.setLong(1, index);
						ChunkWriter.addPlantStmt.setInt(2, coords.w);
						ChunkWriter.addPlantStmt.setInt(3, coords.x);
						ChunkWriter.addPlantStmt.setInt(4, coords.y);
						ChunkWriter.addPlantStmt.setInt(5, coords.z);
						ChunkWriter.addPlantStmt.setLong(6,
								plant.getUpdateTime());
						ChunkWriter.addPlantStmt.setFloat(7,
								plant.getGrowth());
						ChunkWriter.addPlantStmt.setFloat(8,
								plant.getFruitGrowth());
						
						ChunkWriter.addPlantStmt.addBatch();
						
						// execute the statement if we hit 1000 batches
						if ((coordCounter + 1) % 1000 == 0) {
							
							ChunkWriter.addPlantStmt.executeBatch();
							coordCounter = 0;
							needToExec = false;
							try {
								plugin.getPlantManager().getWriteConnection().commit();
							} catch (SQLException e) {
								RealisticBiomes.LOG.warning("Autocommit is probably still on..." + e);
							}
						}
						
					} // end for
					
					// if we have left over statements afterwards, execute them
					if (needToExec) {
						ChunkWriter.addPlantStmt.executeBatch();
						try {
							plugin.getPlantManager().getWriteConnection().commit();
						} catch (SQLException e) {
							RealisticBiomes.LOG.warning("Autocommit is probably still on..." + e);
						}
					}
					try {
						plugin.getPlantManager().getWriteConnection().setAutoCommit(true);
					} catch (SQLException e) {
						RealisticBiomes.LOG.severe("Can't set autocommit?" + e);
					}
					
				} 
			}
		} catch (SQLException e) {
			throw new DataSourceException(
					String.format(
							"Failed to unload the chunk (In PlantChunk, "
									+ "replacing with new data/deleting), index %s, coords %s, PlantChunk obj: %s",
							index, coords, this), e);
		}

		// only set loaded to false and reset the plants HashMap
		// only if we are not caching the entire database
		if (!this.plugin.persistConfig.cacheEntireDatabase) {
			RealisticBiomes.doLog(Level.FINER, String.format("PlantChunk.unload(): clearing hashmap for chunk at %s", coords));
			plants = new HashMap<Coords, Plant>();
			loaded = false;
		} 
	}

}
