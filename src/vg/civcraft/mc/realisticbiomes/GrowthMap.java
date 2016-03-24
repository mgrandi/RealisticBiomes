package vg.civcraft.mc.realisticbiomes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.entity.EntityType;

import vg.civcraft.mc.realisticbiomes.GrowthConfig.Type;
import vg.civcraft.mc.realisticbiomes.utils.MaterialAliases;

/**
 * Would probably be best to have box type as key, with material + data or entity
 */
public class GrowthMap {
	
	private HashMap<Material, GrowthConfig> materialMap = new HashMap<Material, GrowthConfig>();
	private HashMap<EntityType, GrowthConfig> entityMap = new HashMap<EntityType, GrowthConfig>();
	private HashMap<TreeType, GrowthConfig> treeTypeMap = new HashMap<TreeType, GrowthConfig>();
	
	public boolean containsKey(Material material) {
		return materialMap.containsKey(material);
	}
	
	public GrowthConfig get(Material material) {
		return materialMap.get(material);
	}
	
	public GrowthConfig put(Material material, GrowthConfig config, GrowthConfig.Type type) {
		if (type == null) {
			if (MaterialAliases.isColumnBlock(material)) {
				type = GrowthConfig.Type.COLUMN;
			} else {
				type = GrowthConfig.Type.PLANT;
			}
		}
		return materialMap.put(material, config.setType(type));
	}
	
	public boolean containsKey(EntityType entity) {
		return entityMap.containsKey(entity);
	}
	
	public GrowthConfig get(EntityType entity) {
		return entityMap.get(entity);
	}

	public GrowthConfig put(EntityType entity, GrowthConfig config) {
		return entityMap.put(entity, config.setType(GrowthConfig.Type.ENTITY));
	}
	
	public boolean containsKey(TreeType treeType) {
		return treeTypeMap.containsKey(treeType);
	}

	public GrowthConfig get(TreeType treeType) {
		return treeTypeMap.get(treeType);
	}
	
	public GrowthConfig put(TreeType treeType, GrowthConfig config) {
		config.setType(Type.TREE);
		config.setTreeType(treeType);
		return treeTypeMap.put(treeType, config);
	}

	/**
	 * For debugging purposes only
	 */
	public Set<Object> keySet() {
		Set<Object> sets = new HashSet<Object>();
		sets.addAll(materialMap.keySet());
		sets.addAll(entityMap.keySet());
		sets.addAll(treeTypeMap.keySet());
		return sets;
	}

	public void putAll(GrowthMap other) {
		materialMap.putAll(other.materialMap);
		entityMap.putAll(other.entityMap);
		treeTypeMap.putAll(other.treeTypeMap);
	}
}
