package mods.defeatedcrow.plugin.SSector;

import shift.sextiarysector.api.gearforce.item.GearForceItemAPI;
import shift.sextiarysector.api.gearforce.item.IGearForceItem;
import mods.defeatedcrow.common.AMTLogger;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

public class SS2ItemHandler {
	
	private SS2ItemHandler(){}
	
	public static boolean isGFItem(ItemStack item)
	{
		return item != null && item.getItem() instanceof IGearForceItem;
	}
	
	public static int getAmount(ItemStack item)
	{
		if (item == null) return 0;	
		
		int ret = GearForceItemAPI.manager.getSpeed(item);
		return ret;
	}
	
	public static int chargeAmount(ItemStack item, int get, boolean isSimulate)
	{
		if (item == null) return 0;
		
		if (item.getItem() instanceof IGearForceItem)
		{
			int power = ((IGearForceItem)item.getItem()).getMaxPower(item);
			if (power > 3) return 0;
			
			int ret = GearForceItemAPI.manager.addEnergy(item, power, get, isSimulate);
			return ret ;
		}
		return 0;
	}
	
	public static int dischargeAmount(ItemStack item, int ret, boolean isSimulate)
	{
		if (item == null) return 0;
		
		if (item.getItem() instanceof IGearForceItem)
		{
			int power = ((IGearForceItem)item.getItem()).getMaxPower(item);
			
			int get = GearForceItemAPI.manager.reduceEnergy(item, power, ret, isSimulate);
			return get ;
		}
		return 0;
	}

}
