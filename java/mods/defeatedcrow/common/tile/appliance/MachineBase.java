package mods.defeatedcrow.common.tile.appliance;

import mods.defeatedcrow.api.charge.*;
import mods.defeatedcrow.api.energy.IBattery;
import mods.defeatedcrow.common.DCsAppleMilk;
import mods.defeatedcrow.common.config.DCsConfig;
import mods.defeatedcrow.common.config.PropertyHandler;
import mods.defeatedcrow.plugin.IC2.*;
import mods.defeatedcrow.plugin.SSector.SS2ItemHandler;
import mods.defeatedcrow.plugin.cofh.RFItemHandler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModAPIManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * ChargeItemを燃料とするTileEntityのベースクラス。
 * <br>チャージ管理部分のみを共通処理としてここで作っている。
 * <br>レシピとアイテム生産については個々のクラスで作る。
 * */
public abstract class MachineBase extends TileEntity implements ISidedInventory, IChargeableMachine
{
 
	//現在のチャージ量
	private int chargeAmount = 0;
	//チャージアイテムを溶かす際の判定発生間隔
	private int coolTime = 4;
	//作業中カウント
	public int cookTime = 0;
	
	//EU受け入れ用のチャンネル
	protected IEUSinkChannel EUChannel;
	
	public MachineBase() {
		super();
		if (ModAPIManager.INSTANCE.hasAPI("IC2API")) EUChannel = EUSinkManager.getChannel(this, this.getMaxChargeAmount(), 3);
	}
	
	private static int exchangeRateRF()
	{
		//RF -> Charge
		return PropertyHandler.rateRF();
	}
	
	private static int exchangeRateEU()
	{
		//EU -> Charge
		return PropertyHandler.rateEU();
	}
	
	private static int exchangeRateGF()
	{
		//GF -> Charge
		return PropertyHandler.rateGF();
	}
 
	@Override
	public void readFromNBT(NBTTagCompound par1NBTTagCompound)
	{
		if (EUChannel != null) EUChannel.readFromNBT2(par1NBTTagCompound);
		
		super.readFromNBT(par1NBTTagCompound);
		
		NBTTagList nbttaglist = par1NBTTagCompound.getTagList("Items", 10);
		this.itemstacks = new ItemStack[this.getSizeInventory()];
 
		for (int i = 0; i < nbttaglist.tagCount(); ++i)
		{
			NBTTagCompound nbttagcompound1 = (NBTTagCompound)nbttaglist.getCompoundTagAt(i);
			byte b0 = nbttagcompound1.getByte("Slot");
 
			if (b0 >= 0 && b0 < this.itemstacks.length)
			{
				this.itemstacks[b0] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
			}
		}
		
		this.chargeAmount = par1NBTTagCompound.getShort("ChargeAmount");
		this.cookTime = par1NBTTagCompound.getShort("CookTime");
		this.coolTime = par1NBTTagCompound.getByte("CoolTime");
	}
 
	@Override
	public void writeToNBT(NBTTagCompound par1NBTTagCompound)
	{
		if (EUChannel != null) EUChannel.writeToNBT2(par1NBTTagCompound);
		
		super.writeToNBT(par1NBTTagCompound);
		
		NBTTagList nbttaglist = new NBTTagList();
		 
		for (int i = 0; i < this.itemstacks.length; ++i)
		{
			if (this.itemstacks[i] != null)
			{
				NBTTagCompound nbttagcompound1 = new NBTTagCompound();
				nbttagcompound1.setByte("Slot", (byte)i);
				this.itemstacks[i].writeToNBT(nbttagcompound1);
				nbttaglist.appendTag(nbttagcompound1);
			}
		}
		
		par1NBTTagCompound.setTag("Items", nbttaglist);
		
		//燃焼時間や調理時間などの書き込み
		par1NBTTagCompound.setShort("ChargeAmount", (short)this.chargeAmount);
		par1NBTTagCompound.setShort("CookTime", (short)this.cookTime);
		par1NBTTagCompound.setByte("CoolTime", (byte)this.coolTime);
	}
	
	@Override
	public Packet getDescriptionPacket() {
        NBTTagCompound nbtTagCompound = new NBTTagCompound();
        this.writeToNBT(nbtTagCompound);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, nbtTagCompound);
	}
 
	@Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.func_148857_g());
    }
	
	@Override
	public void invalidate() {
		if (EUChannel != null) EUChannel.invalidate2();
		super.invalidate();
	}
	
	//以下はSinkChannel用のメソッド
	@Override
	public void onChunkUnload() {
		if (EUChannel != null) EUChannel.onChunkUnload2();
		super.onChunkUnload();
	}
 
	//調理中の矢印の描画
	@SideOnly(Side.CLIENT)
	public int getCookProgressScaled(int par1)
	{
		return this.cookTime * par1 / 50;
	}
 
	//チャージゲージの描画
	@SideOnly(Side.CLIENT)
	public int getBurnTimeRemainingScaled(int par1)
	{
		return this.chargeAmount * par1 / this.getMaxChargeAmount();
	}
 
	//調理中
	@Override
	public boolean isActive()
	{
		return this.cookTime > 0;
	}
	
	//チャージが満タンである
	public boolean isFullCharged()
	{
		return this.chargeAmount == this.getMaxChargeAmount();
	}
	
	//毎Tickのチャージ消費量を変更可能にする。
	public int getDecrementChargePerTick()
	{
		return 1;
	}
	
	//以下はパケット送受信用メソッド
	public void setChargeAmount(int par1)
	{
		this.chargeAmount = par1;
	}
	
	public int getChargeAmount()
	{
		return this.chargeAmount;
	}
 
	/**
	 * Tick毎の処理。ほぼバニラかまどのパクリ。
	 */
	public void updateEntity()
	{
		boolean flag = this.isFullCharged();
		boolean flag1 = false;
 
		//まずEUChannel更新
		if (!this.worldObj.isRemote && EUChannel != null)
		{
			EUChannel.updateEntity2();
		}
		
		//硬直時間：燃料の消費に利用
		if (this.coolTime > 0)
		{
			--this.coolTime;
		}
 
		if (!this.worldObj.isRemote)
		{
			if (this.coolTime == 0)
			{
				//チャージが満タンではないか？
				if (!flag && this.isItemFuel(this.itemstacks[0]))
				{
					//チャージ残量＋アイテムのチャージ量
					int i = this.chargeAmount + getItemBurnTime(this.itemstacks[0]);
					int j = getItemBurnTime(this.itemstacks[0]);
	 
					if (i <= this.getMaxChargeAmount())//指定したチャージ上限より小さいかどうか
					{
						this.chargeAmount = i;
						flag1 = true;
	 
						if (this.itemstacks[0].getItem() instanceof IBattery)
						{
							//IBatteryの場合、ここでは空容器のスロット移動は行わない。
							IBattery bat = (IBattery) this.itemstacks[0].getItem();
							bat.discharge(this.itemstacks[0], 16, true);
						}
						else//スロット1のアイテムを減らす
						{
							int ret = this.discharge(this.itemstacks[0], j, 0);
							if (ret > 0)
							{
								this.chargeAmount += ret;
								flag = true;
							}
						}
					}
					
					if (this.chargeAmount > this.getMaxChargeAmount())
					{
						this.chargeAmount = this.getMaxChargeAmount();
					}
				}
				
				//からになったIBatteryのスタック移動はここ
				if (this.itemstacks[0] != null && this.itemstacks[0].getItem() instanceof IBattery)
				{
					IBattery bat = (IBattery) this.itemstacks[0].getItem();
					if (bat.getChargeAmount(this.itemstacks[0]) == 0
							&& this.itemstacks[1] == null)
					{
						this.setInventorySlotContents(1, this.itemstacks[0].copy());
						this.decrStackSize(0, 1);
					}
				}
				
				//最後に硬直時間を設定。コンフィグで更新間隔は変えられる
				this.coolTime = DCsConfig.batteryUpdate;
			}
			
			//Chargerから移植した他MODケーブルチェック
			ForgeDirection[] dirs = ForgeDirection.VALID_DIRECTIONS;
			for (ForgeDirection dir : dirs)
			{
				if (!this.isFullCharged())
				{
					int accept = this.acceptChargeFromDir(dir);
					int cap = this.getMaxChargeAmount() - this.getChargeAmount();
					accept = Math.min(accept, cap);
					this.chargeAmount += accept;
				}
				
			}
			
			/*
			 * 調理の待ち時間&チャージ減少処理。
			 * 100tickの調理時間が終わるまでは、tick毎にチャージが減っていく。途中で0になったら調理が振り出しに戻る。
			 * */
			if (this.getChargeAmount() >= this.getDecrementChargePerTick() && this.canSmelt())
			{
				++this.cookTime;
				this.chargeAmount -= this.getDecrementChargePerTick();
				if (this.chargeAmount < 0) this.chargeAmount = 0;

				if (this.cookTime == 50)
				{
					this.cookTime = 0;
					this.onProgress();
					flag1 = true;
				}
			}
			else
			{
				this.cookTime = 0;
			}

			if (this.getChargeAmount() < 1)
			{
				flag1 = true;
			}
			
			
			this.markDirty();
		}
	}
 
	/**
	 * アイテムがIceMakerにレシピ登録された材料かどうか
	 * レシピ登録はTeaMakerと類似の登録制を使用
	 */
	public abstract boolean canSmelt();
 
	/**
	 * 実際に材料を消費して、完成スロットにアウトプットを返すためのメソッド
	 */
	public abstract void onProgress();
 
	/**
	 * このアイテムのチャージ量
	 * @param par0ItemStack チェック対象アイテム
	 */
	public static int getItemBurnTime(ItemStack par0ItemStack)
	{
		if (par0ItemStack == null)
		{
			return 0;
		}
		else
		{
			int ret = 0;
			int inc = 16; //速度はチャージバッテリーと同じ
			
			if (Loader.isModLoaded("SextiarySector") && ret == 0)
			{
				int i  = SS2ItemHandler.dischargeAmount(par0ItemStack, inc * exchangeRateGF(), true);
				ret = Math.round(i / exchangeRateGF());
			}
			if (ModAPIManager.INSTANCE.hasAPI("CoFHAPI|energy") && ret == 0)
			{
				int i  = RFItemHandler.dischargeAmount(par0ItemStack, inc * exchangeRateRF(), true);
				ret = Math.round(i / exchangeRateRF());
			}
			if (ModAPIManager.INSTANCE.hasAPI("IC2API") && ret == 0)
			{
				int i  = EUItemHandler.dischargeAmount(par0ItemStack, inc * exchangeRateEU(), true);
				ret = Math.round(i / exchangeRateEU());
			}
			if (ret == 0)
			{
				if (ChargeItemManager.chargeItem.getChargeAmount(par0ItemStack) > 0)
				{
					ret = ChargeItemManager.chargeItem.getChargeAmount(par0ItemStack);
				}
				else if (par0ItemStack.getItem() instanceof IBattery)
				{
					//充電池の場合、16/4tickずつ減少する。
					IBattery bat = (IBattery) par0ItemStack.getItem();
					ret = bat.discharge(par0ItemStack, 16, false);
				}
			}
			return ret;
		}
	}
 
	/**
	 * このアイテムがチャージできる燃料であるかどうか
	 * @param par0ItemStack チェック対象アイテム
	 */
	public static boolean isItemFuel(ItemStack par0ItemStack)
	{
		return getItemBurnTime(par0ItemStack) > 0;
	}
	
	/**
	 * 燃料スロットの電池アイテムを処理するメソッド
	 */
	public int discharge(ItemStack item, int amount, int slot)
	{
		if (item == null)
		{
			return 0;
		}
		else
		{
			int ret = 0;
			int inc = amount;
			
			if (Loader.isModLoaded("SextiarySector") && ret == 0)
			{
				int i  = SS2ItemHandler.dischargeAmount(item, inc * exchangeRateGF(), false);
				ret = Math.round(i / exchangeRateGF());
				
				if (ret > 0 && SS2ItemHandler.getAmount(item) == 0 && this.itemstacks[1] == null)
				{
					if (item == null || item.stackSize == 0)
					{
						this.setInventorySlotContents(0, null);
					}
					else
					{
						this.setInventorySlotContents(1, item.copy());
						this.decrStackSize(slot, 1);
					}
				}
			}
			if (ModAPIManager.INSTANCE.hasAPI("CoFHAPI|energy") && ret == 0)
			{
				int i  = RFItemHandler.dischargeAmount(item, inc * exchangeRateRF(), false);
				ret = Math.round(i / exchangeRateRF());
				
				if (ret > 0 && RFItemHandler.getAmount(item) == 0 && this.itemstacks[1] == null)
				{
					if (item == null || item.stackSize == 0)
					{
						this.setInventorySlotContents(0, null);
					}
					else
					{
						this.setInventorySlotContents(1, item.copy());
						this.decrStackSize(slot, 1);
					}
					
				}
			}
			if (ModAPIManager.INSTANCE.hasAPI("IC2API") && ret == 0)
			{
				int i  = EUItemHandler.dischargeAmount(item, inc * exchangeRateEU(), false);
				ret = Math.round(i / exchangeRateEU());
				
				if (ret > 0 && EUItemHandler.getAmount(item) == 0 && this.itemstacks[1] == null)
				{
					if (item == null || item.stackSize == 0)
					{
						this.setInventorySlotContents(0, null);
					}
					else
					{
						this.setInventorySlotContents(1, item.copy());
						this.decrStackSize(slot, 1);
					}
				}
			}
			
			if (ret == 0)
			{
				this.decrStackSize(slot, 1);
				ret = amount;
			}
			
			return ret;
		}
	}
	
	/**
	 * この装置のupdateで、周囲の受け入れ可能装置をサーチしてチャージを得る。
	 * そのため、AMT2装置にチャージを送りたい装置は基本的にはIChargeGeneratorを実装して隣接されれば良い。
	 */
	public int acceptChargeFromDir(ForgeDirection dir)
	{
		int ret = 0;
		
		//IChargeGeneratorからチャージを受け取れるように
		ForgeDirection opposite = dir.getOpposite();
		TileEntity tile = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
		if (tile instanceof IChargeGenerator)
		{
			IChargeGenerator device = (IChargeGenerator) tile;
			int get = device.generateCharge(opposite, true);
			
			if (get > 0)
			{
				device.generateCharge(opposite, false);
				ret = get;
			}
		}
		
		//EU受入量は指定する必要があるので、とりあえず512とする。
		if (ret == 0 && EUChannel != null){
			
			int i = this.getChargeAmount();
			double eu = Math.min(EUChannel.getEnergyStored2(), 512);
			double get = eu / this.exchangeRateEU();
			if ((this.getMaxChargeAmount() - i) < get) return 0;
			
			if (EUChannel.useEnergy2(eu)){
				ret = (int) get;
			}
		}
		return ret;
	}
	
	/* IChargeableMachineのメソッド */
	
	//チャージゲージ上限も変更可能に。
	@Override
	public int getMaxChargeAmount()
	{
		return 25600;
	}
	
	//チャージに空きがあり、燃料スロットのアイテムを受け入れられる状態
	@Override
	public boolean canReceiveChargeItem(ItemStack item)
	{
		boolean flag = false;
		boolean flag2 = false;
		if (item != null)
		{
			int i = this.getItemBurnTime(item);
			flag = i > 0 && (this.getChargeAmount() + i <= this.getMaxChargeAmount());
		}
		
		if (this.getStackInSlot(0) == null)
		{
			flag2 = true;
		}
		else
		{
			ItemStack current = this.getStackInSlot(0);
			flag2 = item.isItemEqual(current) && (current.stackSize + item.stackSize < current.getMaxStackSize());
		}
		
		return flag && flag2;
	}
	
	@Override
	public int addCharge(int amount, boolean isSimulate)
	{
		int eng = this.getChargeAmount();
		int get = amount;
		if (this.isFullCharged()) return 0;
		
		int ret = Math.min(this.getMaxChargeAmount() - eng, get);
		
		if (!isSimulate){
			this.setChargeAmount(eng + ret);
		}
		
		return ret;
	}
	
	@Override
	public int extractCharge(int amount, boolean isSimulate)
	{
		int eng = this.getChargeAmount();
		int get = amount;
		
		int ret = Math.min(eng, get);
		
		if (!isSimulate){
			this.setChargeAmount(eng - ret);
		}
		
		return ret;
	}
	
	/* ========== 以下、ISidedInventoryのメソッド ==========*/
	
	/*
	 * 0 : 燃料搬入
	 * 1 : 燃料の空容器搬出
	 * 2~ : 各Tileで実装される。
	 * */
	protected abstract int[] slotsTop();
	protected abstract int[] slotsBottom();
	protected abstract int[] slotsSides();
 
	public ItemStack[] itemstacks = new ItemStack[getSizeInventory()];
 
 
	//スロット数は各Tileでオーバーライドして増やすこと。2は最低限の値。
	@Override
	public int getSizeInventory() {
		return 2;
	}
 
	//インベントリ内の任意のスロットにあるアイテムを取得
	@Override
	public ItemStack getStackInSlot(int par1) {
		return par1 < this.getSizeInventory() ? this.itemstacks[par1] : null;
	}
 
	@Override
	public ItemStack decrStackSize(int par1, int par2) {
		if (this.itemstacks[par1] != null)
		{
			ItemStack itemstack;
 
			if (this.itemstacks[par1].stackSize <= par2)
			{
				itemstack = this.itemstacks[par1];
				this.itemstacks[par1] = null;
				return itemstack;
			}
			else
			{
				itemstack = this.itemstacks[par1].splitStack(par2);
 
				if (this.itemstacks[par1].stackSize == 0)
				{
					this.itemstacks[par1] = null;
				}
 
				return itemstack;
			}
		}
		else
		{
			return null;
		}
	}
 
	@Override
	public ItemStack getStackInSlotOnClosing(int par1) {
		if (this.itemstacks[par1] != null)
		{
			ItemStack itemstack = this.itemstacks[par1];
			this.itemstacks[par1] = null;
			return itemstack;
		}
		else
		{
			return null;
		}
	}
 
	// インベントリ内のスロットにアイテムを入れる
	@Override
	public void setInventorySlotContents(int par1, ItemStack par2ItemStack) {
		
		if (par1 > this.getSizeInventory()) par1 = 0;//存在しないスロットに入れようとすると強制的に材料スロットに変更される。
		
		this.itemstacks[par1] = par2ItemStack;
 
		if (par2ItemStack != null && par2ItemStack.stackSize > this.getInventoryStackLimit())
		{
			par2ItemStack.stackSize = this.getInventoryStackLimit();
		}
	}
 
	// インベントリの名前
	@Override
	public abstract String getInventoryName();
 
	// 多言語対応かどうか
	@Override
	public boolean hasCustomInventoryName() {
		return true;
	}
 
	// インベントリ内のスタック限界値
	@Override
	public int getInventoryStackLimit() {
		return 64;
	}
 
	@Override
	public void markDirty() {
		super.markDirty();
	}
 
	// par1EntityPlayerがTileEntityを使えるかどうか
	@Override
	public boolean isUseableByPlayer(EntityPlayer par1EntityPlayer) {
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) != this ? false : par1EntityPlayer.getDistanceSq((double) this.xCoord + 0.5D, (double) this.yCoord + 0.5D, (double) this.zCoord + 0.5D) <= 64.0D;
	}
 
	@Override
	public void openInventory() {}
 
	@Override
	public void closeInventory() {}
 
	@Override
	public boolean isItemValidForSlot(int par1, ItemStack par2ItemStack) {
		return (par1 == 1 || par1 > 10) ? false : (par1 == 0 ? this.isItemFuel(par2ItemStack) : true);
	}
 
	//ホッパーにアイテムの受け渡しをする際の優先度
	@Override
	public int[] getAccessibleSlotsFromSide(int par1) {
		return par1 == 0 ? slotsBottom() : (par1 == 1 ? slotsTop() : slotsSides());
	}
 
	//ホッパーからアイテムを入れられるかどうか
	@Override
	public boolean canInsertItem(int par1, ItemStack par2ItemStack, int par3) {
		return this.isItemValidForSlot(par1, par2ItemStack);
	}
 
	//隣接するホッパーにアイテムを送れるかどうか
	@Override
	public boolean canExtractItem(int par1, ItemStack par2ItemStack, int par3) {
		return true;
	}
}
