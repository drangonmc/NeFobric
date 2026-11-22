package net.minecraftforge.energy;

/** 能量（FE）存储，能量能力的接口。数值都是整数。 */
public interface IEnergyStorage {
    /** 存入最多 maxReceive 能量，返回实际存入（simulate 为 true 时只计算不改变）。 */
    int receiveEnergy(int maxReceive, boolean simulate);

    /** 取出最多 maxExtract 能量，返回实际取出。 */
    int extractEnergy(int maxExtract, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStored();

    boolean canExtract();

    boolean canReceive();
}
