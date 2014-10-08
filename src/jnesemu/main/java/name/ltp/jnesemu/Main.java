/*
Copyright (c) 2011-2014 Joel Yliluoma 'Bisqwit', Timofey Lagutin 'lostdj'

This software is provided 'as-is', without any express or implied
warranty. In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
*/

package name.ltp.jnesemu;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class Main
{
	public static void main(String[] args)
	{
		run(args[0]);
	}

	public static void run(String pa)
	{
		byte[] d;
		try
		{
			Path p = FileSystems.getDefault().getPath("", pa);
			d = Files.readAllBytes(p);
		}
		catch(Exception ignored) { d = new byte[0]; }

		int i = 0;
		if((d[i++]&0xFF)!='N' || (d[i++]&0xFF)!='E' || (d[i++]&0xFF)!='S' || (d[i++]&0xFF)!='\32')
		{
			System.err.println("Bad header.");

			System.exit(1);
		}

		short rom16count = (short)(d[i++]&0xFF);
		short vrom8count = (short)(d[i++]&0xFF);
		short ctrlbyte   = (short)(d[i++]&0xFF);
		short mappernum  = (short)((d[i++]&0xFF) | (ctrlbyte >> 4));
		++i;++i;++i;++i;++i;++i;++i;++i;
		if(mappernum >= 0x40) mappernum &= 15;
		GamePak.mappernum = mappernum;

		if(rom16count != 0)
		{
			GamePak.ROM = new short[rom16count * 0x4000];
			for(int j = 0; j < rom16count * 0x4000; j++)
				GamePak.ROM[j] = (short)(d[i++]&0xFF);
		}
		if(vrom8count != 0)
		{
			GamePak.VRAM = new mshort[vrom8count * 0x2000];
			for(int j = 0; j < vrom8count * 0x2000; j++)
				GamePak.VRAM[j] = new mshort((short)(d[i++]&0xFF));
		}

		d = null;
		System.out.println(
			rom16count+" * 16kB ROM, "
				+vrom8count+" * 8kB VROM, "
				+"mapper "+mappernum
				+", ctrlbyte "+ctrlbyte);

		GamePak.Init();
		IO.Init();
		PPU.reg.data = 0;

		for(int a = 0; a < 0x800; ++a)
			CPU.RAM[a] = (short)((a & 4) != 0 ? 0xFF : 0x00);

		for(;;)
			CPU.Op();
	}

	public static interface SDL extends Library
	{
		SDL i = (SDL) Native.loadLibrary("SDL", SDL.class);

		static final int SDL_INIT_VIDEO = 0x00000020;

		int SDL_Init(int flags);
		int SDL_InitSubSystem(int flags);

		static class SDL_Surface extends Structure
		{
			public static class ByReference extends SDL_Surface implements Structure.ByReference {}

			public int flags;
			//SDL_PixelFormat* format;
				public IntByReference format = new IntByReference();
			public int w, h;
			public short pitch;
			//void* pixels;
				public IntByReference pixels = new IntByReference();
			public int offset;

			//struct private_hwdata *hwdata;
				public IntByReference hwdata = new IntByReference();

			//SDL_Rect clip_rect;
				public long r1;
			public int unused1;

			public int locked;

			//struct SDL_BlitMap *map;
				public IntByReference map = new IntByReference();

			public int format_version;

			public int refcount;

			protected List getFieldOrder()
			{
				return Arrays.asList
				(
					"flags", "format", "w", "h", "pitch", "pixels", "offset", "hwdata",
					"r1", "unused1", "locked", "map", "format_version", "refcount"
				);
			}
		}

		SDL_Surface.ByReference SDL_SetVideoMode(int width, int height, int bpp, int flags);
		int SDL_Flip(SDL_Surface.ByReference screen);
	}

	static class mshort
	{
		short v;

		mshort()
		{
			;
		}

		mshort(short v)
		{
			this.v = v;
		}
	}

	static class Tup2<A, B>
	{
		public A a;
		public B b;

		static <A, B> Tup2<A, B> with(final A value0, final B value1)
		{
			return new Tup2<>(value0, value1);
		}

		Tup2(A a, B b)
		{
			this.a = a;
			this.b = b;
		}
	}

	static class RegBit
	{
		long data;

		class f
		{
			final long bitno;
			final long nbits;
			final long mask;

			long get()
			{
				return (data >> bitno) & mask;
			}

			void set(long val)
			{
				data = (data & ~(mask << bitno)) | ((nbits > 1 ? val & mask : (val != 0 ? 1 : 0)) << bitno);
			}

			long preinc()
			{
				set(get() + 1);

				return get();
			}

			long posinc()
			{
				long val = get();
				set(val + 1);

				return val;
			}

			f(final long bitno, final long nbits)
			{
				this.bitno = bitno;
				this.nbits = nbits;
				mask = (1L << nbits) - 1L;
			}

			f(final long bitno)
			{
				this(bitno, 1);
			}
		}
	}

	static class IO
	{
//		static Unsafe unsafe;
//		static
//		{
//			try
//			{
//				Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
//				singleoneInstanceField.setAccessible(true);
//				unsafe = (Unsafe)singleoneInstanceField.get(null);
//			}
//			catch(Exception ignored) { System.err.println("Can't get ahold of Unsafe."); }
//		}

		static SDL.SDL_Surface.ByReference sdl;
		static void Init()
		{
			SDL.i.SDL_Init(SDL.SDL_INIT_VIDEO);
			SDL.i.SDL_InitSubSystem(SDL.SDL_INIT_VIDEO);
			sdl = SDL.i.SDL_SetVideoMode(256, 240, 32, 0);
		}

		static final long palette[][][] = new long[3][64][512];
		static long prev = 0xFFFFFFFFL;
		static final short s[] = {'\372', '\273', '\32', '\305', '\35', '\311', 'I', '\330', 'D', '\357', '}', '\13', 'D', '!', '}', 'N'};

		static void PutPixel(final long px, final long py, /*final */long pixel, final int offset)
		{
			if(prev == 0xFFFFFFFFL)
				for(int o = 0; o < 3; ++o)
				for(int u = 0; u < 3; ++u)
				for(int p0 = 0; p0 < 512; ++p0)
				for(int p1 = 0; p1 < 64; ++p1)
				{
					int y = 0, i = 0, q = 0;
					for(int p = 0; p < 12; ++p)
					{
						long r = (p + o * 4) % 12;
						pixel = r < 8 - u * 2 ? p0 : p1;

						long c = pixel % 16;
						long l = c < 0xEL ? pixel / 4 & 12 : 4;
						long e = p0 / 64;

						int b = 40 + s[(int)(((c > 12 * (((c + 8 + p) % 12 < 6) ? 1 : 0)) ? 1 : 0) + 2 * ((0451326L >> p / 2 * 3 & e) == 0 ? 1 : 0) + l)];

						y += b;
						i += b * (int)(Math.cos(Math.PI * p / 6) * 5909);
						q += b * (int)(Math.sin(Math.PI * p / 6) * 5909);
					}

					class h
					{
						float gammafix(float f) { return f <= 0.F ? 0.F : (float)Math.pow(f, 2.2D / 1.8D); }
						int clamp(long v) { return v > 255 ? 255 : (int)v; }
					} h h = new h();

					if(u == 2) palette[o][p1][p0] += 0x10000L * h.clamp((long)(255 * h.gammafix(y / 1980.f + i *  0.947F/9e6F + q *  0.624F / 9e6F)));
					if(u == 1) palette[o][p1][p0] += 0x00100L * h.clamp((long)(255 * h.gammafix(y / 1980.f + i * -0.275F/9e6F + q * -0.636F / 9e6F)));
					if(u == 0) palette[o][p1][p0] += 0x00001L * h.clamp((long)(255 * h.gammafix(y / 1980.f + i * -1.109F/9e6F + q *  1.709F / 9e6F)));
				}

			//unsafe.putInt(Pointer.nativeValue(sdl.pixels.getPointer()) + ((py * 256 + px)*4), (int)(palette[offset][(int)(prev % 64)][(int)pixel]));
			sdl.pixels.getPointer().setInt((py * 256 + px)*4, (int)(palette[offset][(int)(prev % 64)][(int)pixel]));
			prev = pixel;
		}

		static void FlushScanline(long py)
		{
			if(py == 239) SDL.i.SDL_Flip(sdl);
		}

		static int[] joy_current = {0, 0}, joy_next={0, 0}, joypos={0, 0};
		static void JoyStrobe(long v)
		{
			if(v != 0) { joy_current[0] = joy_next[0]; joypos[0] = 0; }
			if(v != 0) { joy_current[1] = joy_next[1]; joypos[1] = 0; }
		}

		static final short masks[] = {0x20, 0x10, 0x40, 0x80, 0x04, 0x08, 0x02, 0x01};
		static short JoyRead(int idx)
		{
			return (joy_current[idx] & masks[joypos[idx]++ & 7]) != 0 ? (short)1 : 0;
		}
	}

	static class GamePak
	{
		static short[] ROM = new short[0];
		static mshort[] VRAM = new mshort[0x2000];
		static void VRAMinit() { for(int i=0; i<VRAM.length; i++) VRAM[i]=new mshort(); }
		static { VRAMinit(); }

		static long mappernum;
		static final long ROM_Granularity = 0x2000L;
		static final long ROM_Pages = 0x10000 / ROM_Granularity;
		static final long VROM_Granularity = 0x0400L;
		static final long VROM_Pages = 0x2000L / VROM_Granularity;
		static final mshort NRAM[] = new mshort[0x1000]; static { for(int i=0; i<NRAM.length; i++) NRAM[i]=new mshort(); }
		static final byte PRAM[] = new byte[0x2000];
		static int banks[] = new int[(int)ROM_Pages];
		static int Vbanks[] = new int[(int)VROM_Pages];
		static public final mshort Nta[] = {NRAM[0x0000], NRAM[0x0400], NRAM[0x0000], NRAM[0x0400]};

		static short regs[] = {0x0C,0,0,0}, counter=0, cache=0;
		static short sel[][] = { {0,0,0,0}, {1,1,1,1}, {0,1,0,1}, {0,0,1,1} };

		static void SetROM(long size, long baseaddr, long index)
		{
			for(int v = ROM.length + (int)(index * size), p = (int)(baseaddr / ROM_Granularity);
			        p < (baseaddr + size) / ROM_Granularity && p < ROM_Pages;
			        ++p, v += ROM_Granularity)
				banks[p] = v % ROM.length;
		}

		static void SetVROM(long size, long baseaddr, long index)
		{
			for(int v = VRAM.length + (int)(index * size), p = (int)(baseaddr / VROM_Granularity);
					p < (baseaddr + size) / VROM_Granularity && p < VROM_Pages;
					++p, v += VROM_Granularity)
				Vbanks[p] = v % VRAM.length;
		}

		static short Access(long addr, short value, boolean write)
		{
			if(write && addr >= 0x8000 && mappernum == 7) // e.g. Rare games
			{
				SetROM(0x8000, 0x8000, (value&7));
				Nta[0] = Nta[1] = Nta[2] = Nta[3] = NRAM[0x400 * ((value>>4)&1)];
			}
			if(write && addr >= 0x8000 && mappernum == 2) // e.g. Rockman, Castlevania
			{
				SetROM(0x4000, 0x8000, value);
			}
			if(write && addr >= 0x8000 && mappernum == 3) // e.g. Kage, Solomon's Key
			{
				value &= Access(addr, (short)0, false); // Simulate bus conflict
				SetVROM(0x2000, 0x0000, (value&3));
			}
			if(write && addr >= 0x8000 && mappernum == 1) // e.g. Rockman 2, Simon's Quest
			{
				if((value & 0x80) != 0) { regs[0]=0x0C; /*goto configure;*/ }
				if((value & 0x80) == 0) cache |= (value&1) << counter;
				if((value & 0x80) == 0) ++counter;
				if((value & 0x80) != 0 || counter == 5)
				{
					if((value & 0x80) == 0) regs[ (int)((addr>>13) & 3) ] = value = cache;
					configure:
					cache = counter = 0;
					for(int m=0; m<4; ++m) Nta[m] = NRAM[0x400 * sel[regs[0]&3][m]];
					SetVROM(0x1000, 0x0000, ((regs[0]&16) != 0 ? regs[1] : ((regs[1]&~1)+0)));
					SetVROM(0x1000, 0x1000, ((regs[0]&16) != 0 ? regs[2] : ((regs[1]&~1)+1)));
					switch( (regs[0]>>2)&3 )
					{
						case 0: case 1:
							SetROM(0x8000, 0x8000, (regs[3] & 0xE) / 2);
						break;
						case 2:
							SetROM(0x4000, 0x8000, 0);
							SetROM(0x4000, 0xC000, (regs[3] & 0xF));
							break;
						case 3:
							SetROM(0x4000, 0x8000, (regs[3] & 0xF));
							SetROM(0x4000, 0xC000, ~0);
							break;
					}
				}
			}

			if((addr >> 13) == 3)
				return PRAM[(int)(addr & 0x1FFF)];

			return ROM[banks[(int)((addr / ROM_Granularity) % ROM_Pages)] + (int)(addr % ROM_Granularity)];
		}

		static void Init()
		{
			SetVROM(0x2000, 0x0000, 0);
			for(int v=0; v<4; ++v) SetROM(0x4000, v*0x4000, v==3 ? -1 : 0);
		}
	}

	static class PPU
	{
		static class reg extends RegBit
		{
			f sysctrl    = new f(0, 8);
			f BaseNTA    = new f(0, 2);
			f Inc        = new f(2);
			f SPaddr     = new f(3);
			f BGaddr     = new f(4);
			f SPsize     = new f(5);
			f SlaveFlag  = new f(6);
			f NMIenabled = new f(7);

			f dispctrl   = new f(8, 8);
			f Grayscale  = new f(8);
			f ShowBG8    = new f(9);
			f ShowSP8    = new f(10);
			f ShowBG     = new f(11);
			f ShowSP     = new f(12);
			f ShowBGSP   = new f(11, 2);
			f EmpRGB     = new f(13, 3);

			f status     = new f(16, 8);
			f SPoverflow = new f(21);
			f SP0hit     = new f(22);
			f InVBlank   = new f(23);

			f OAMaddr    = new f(24, 8);
			f OAMdata    = new f(24, 2);
			f OAMindex   = new f(26, 6);
		} static reg reg = new reg();

		static mshort palette[] = new mshort[32]; static { for(int i=0; i<palette.length; i++) palette[i]=new mshort(); }
		static short OAM[] = new short[256];

		static class Sprite
		{
			short sprindex;
			short y;
			short index;
			short attr;
			short x;
			int pattern;

			void set(Sprite o)
			{
				sprindex = o.sprindex;
				y = o.y;
				index = o.index;
				attr = o.attr;
				x = o.x;
				pattern = o.pattern;
			}
		}
		static Sprite[] OAM2 = new Sprite[8], OAM3 = new Sprite[8];
		static { for(int i=0; i<OAM2.length; i++) OAM2[i]=new Sprite(); }
		static { for(int i=0; i<OAM3.length; i++) OAM3[i]=new Sprite(); }

		static class scrolltype extends RegBit
		{
			f raw = new f(3, 16);
			f xscroll = new f(0, 8);
			f xfine = new f(0, 3);
			f xcoarse = new f(3, 5);
			f ycoarse = new f(8, 5);
			f basenta = new f(13, 2);
			f basenta_h = new f(13);
			f basenta_v = new f(14);
			f yfine = new f(15, 3);
			f vaddrhi = new f(11, 8);
			f vaddrlo = new f(3, 8);
		} static scrolltype scroll = new scrolltype(), vaddr = new scrolltype();

		static long pat_addr;
		static long sprinpos;
		static long sproutpos;
		static long sprrenpos;
		static long sprtmp;

		static int tileattr;
		static int tilepat;
		static int ioaddr;

		static long bg_shift_pat;
		static long bg_shift_attr;

		static int scanline = 241;
		static int x = 0;
		static int scanline_end = 341;
		static int VBlankState = 0;
		static int cycle_counter = 0;

		static int read_buffer = 0;
		static int open_bus = 0;
		static int open_bus_decay_timer = 0;

		static boolean even_odd_toggle = false;
		static boolean offset_toggle = false;

		static mshort mmap(int i)
		{
			i &= 0x3FFF;
			if(i >= 0x3F00)
			{
				if(i % 4 == 0) i &= 0x0F;
				return palette[i & 0x1F];
			}
			if(i < 0x2000) return GamePak.VRAM[GamePak.Vbanks[(int)((i / GamePak.VROM_Granularity) % GamePak.VROM_Pages)]
			                            + (int)(i % GamePak.VROM_Granularity)];
			return                GamePak.NRAM[GamePak.Nta[(i >> 10) & 3].v];
		}

		static int RefreshOpenBus(int v)
		{
			open_bus_decay_timer = 77777;

			return open_bus = v;
		}

		static int Access(int index, short v, boolean write)
		{
			int res = open_bus;
			if(write) RefreshOpenBus(v);
			switch(index)
			{
				case 0: if(write) { reg.sysctrl .set(v); scroll.basenta.set(reg.BaseNTA.get()); } break;
				case 1: if(write) { reg.dispctrl.set(v); } break;
				case 2: if(write) break;
					res = (int)reg.status.get() | ((int)open_bus & 0x1F);
					reg.InVBlank.set(0 /*false*/);
					offset_toggle = false;
					if(VBlankState != -5)
						VBlankState = 0;
					break;
				case 3: if(write) reg.OAMaddr     .set(v); break;
				case 4: if(write) OAM[(int)reg.OAMaddr.posinc()] = v;
				        else res = RefreshOpenBus(OAM[(int)reg.OAMaddr.get()] & (reg.OAMdata.get() == 2 ? 0xE3 : 0xFF));
					break;
				case 5: if(!write) break;
					if(offset_toggle) { scroll.yfine  .set(v & 7); scroll.ycoarse.set(v >> 3); }
					else              { scroll.xscroll.set(v); }
					offset_toggle = !offset_toggle;
					break;
				case 6: if(!write) break;
					if(offset_toggle) { scroll.vaddrlo.set(v); vaddr.raw.set(scroll.raw.get()); }
					else              { scroll.vaddrhi.set(v & 0x3F); }
					offset_toggle = !offset_toggle;
					break;
				case 7:
					res = read_buffer;
					mshort t = mmap((int)vaddr.raw.get());
					if(write) res = t.v = v;
					else { if((vaddr.raw.get() & 0x3F00) == 0x3F00)
						res = read_buffer = (open_bus & 0xC0) | (t.v & 0x3F);
						read_buffer = t.v; }
					RefreshOpenBus(res);
					vaddr.raw.set(vaddr.raw.get() + (reg.Inc.get() != 0 ? 32 : 1));
					break;
			}

			return res;
		}

		static void rendering_tick()
		{
			boolean tile_decode_mode = (0x10FFFFL & (1L << (x / 16))) != 0;

			switch(x % 8)
			{
				case 2:
					ioaddr = (int)(0x23C0 + 0x400 * vaddr.basenta.get() + 8 * (vaddr.ycoarse.get() / 4) + (vaddr.xcoarse.get() / 4));
					if(tile_decode_mode) break;
				case 0:
					ioaddr = (int)(0x2000 + (vaddr.raw.get() & 0xFFF));
					if(x == 0) { sprinpos = sproutpos = 0; if(reg.ShowSP.get() != 0) reg.OAMaddr.set(0); }
					if(reg.ShowBG.get() == 0) break;
					if(x == 304 && scanline == -1) vaddr.raw.set((scroll.raw.get()&0xFFFFFFFFL));
					if(x == 256) { vaddr.xcoarse.set((scroll.xcoarse.get()&0xFFFFFFFFL));
						vaddr.basenta_h.set(scroll.basenta_h.get()&0xFFFFFFFFL);
						sprrenpos = 0; }
					break;
				case 1:
					if(x == 337 && scanline == -1 && even_odd_toggle && reg.ShowBG.get() != 0) scanline_end = 340;
					pat_addr = 0x1000 * reg.BGaddr.get() + 16 * mmap(ioaddr).v + vaddr.yfine.get();
					if(!tile_decode_mode) break;
					bg_shift_pat  = (bg_shift_pat  >> 16) + 0x00010000 * tilepat;
					bg_shift_attr = (bg_shift_attr >> 16) + 0x55550000 * tileattr;
					break;
				case 3:
					if(tile_decode_mode)
					{
						tileattr = (int)((mmap(ioaddr).v >> ((vaddr.xcoarse.get() & 2) + 2 * (vaddr.ycoarse.get() & 2))) & 3);
						if(vaddr.xcoarse.preinc() == 0) { vaddr.basenta_h.set(1 - vaddr.basenta_h.get()); }
						if(x == 251 && vaddr.yfine.preinc() == 0 && vaddr.ycoarse.preinc() == 30)
						{ vaddr.ycoarse.set(0); vaddr.basenta_v.set(1 - vaddr.basenta_v.get()); }
					}
					else if(sprrenpos < sproutpos)
					{
						Sprite o = OAM3[(int)sprrenpos];
						o.set(OAM2[(int)sprrenpos]);
						long y = (scanline) - o.y;
						if((o.attr & 0x80) != 0) y ^= (reg.SPsize.get() != 0 ? 15 : 7);
						pat_addr = 0x1000 * (reg.SPsize.get() != 0 ? (o.index & 0x01) : reg.SPaddr.get());
						pat_addr +=  0x10 * (reg.SPsize.get() != 0 ? (o.index & 0xFE) : (o.index & 0xFF));
						pat_addr += (y & 7) + (y & 8) * 2;
					}
					break;
				case 5:
					tilepat = mmap((int)(pat_addr | 0)).v;
					break;
				case 7:
					long p = tilepat | ((mmap((int)(pat_addr | 8)).v&0xFF) << 8);
					p = (p&0xF00FL) | ((p&0x0F00L)>>4) | ((p&0x00F0L)<<4);
					p = (p&0xC3C3L) | ((p&0x3030L)>>2) | ((p&0x0C0CL)<<2);
					p = (p&0x9999L) | ((p&0x4444L)>>1) | ((p&0x2222L)<<1);
					tilepat = (int)p;
					if(!tile_decode_mode && sprrenpos < sproutpos)
						OAM3[(int)sprrenpos++].pattern = tilepat;
					break;
			}
			// (TODO: implement crazy 9-sprite malfunction)
			switch((x >= 64 && x < 256 && ((x % 2) != 0)) ? (int)(reg.OAMaddr.posinc() & 3) : 4)
			{
				default:
					sprtmp = OAM[(int)reg.OAMaddr.get()];
					break;
				case 0:
					if(sprinpos >= 64) { reg.OAMaddr.set(0); break; }
					++sprinpos;
					if(sproutpos<8) OAM2[(int)sproutpos].y = (short)(sprtmp&0xFFFF);
					if(sproutpos<8) OAM2[(int)sproutpos].sprindex = (short)reg.OAMindex.get();
					{long y1 = sprtmp, y2 = sprtmp + (reg.SPsize.get() != 0 ? 16 : 8);
						if(!(scanline >= y1 && scanline < y2))
							reg.OAMaddr.set(sprinpos != 2 ? reg.OAMaddr.get() + 3 : 8);}
					break;
				case 1:
					if(sproutpos < 8) OAM2[(int)sproutpos].index = (short)sprtmp;
					break;
				case 2:
					if(sproutpos < 8) OAM2[(int)sproutpos].attr = (short)sprtmp;
					break;
				case 3:
					if(sproutpos < 8) OAM2[(int)sproutpos].x = (short)sprtmp;
					if(sproutpos < 8) ++sproutpos; else reg.SPoverflow.set(1 /*true*/);
					if(sprinpos == 2) reg.OAMaddr.set(8);
					break;
			}
		}

		static void render_pixel()
		{
			boolean edge   = ((x + 8)&0xFF) < 16; // 0..7, 248..255
			boolean showbg = reg.ShowBG.get() != 0 && (!edge || reg.ShowBG8.get() != 0);
			boolean showsp = reg.ShowSP.get() != 0 && (!edge || reg.ShowSP8.get() != 0);

			long fx = scroll.xfine.get();
			long xpos = 15 - (( (x &7) + fx + 8 * ((x & 7) != 0 ? 1 : 0) ) & 15);

			long pixel = 0;
			long attr = 0;
			if(showbg)
			{
				pixel = (bg_shift_pat  >> (xpos * 2)) & 3;
				attr  = (bg_shift_attr >> (xpos * 2)) & (pixel != 0 ? 3 : 0);
			}
			else if((vaddr.raw.get() & 0x3F00) == 0x3F00 && reg.ShowBGSP.get() == 0)
				pixel = vaddr.raw.get();

			if(showsp)
				for(long sno = 0; sno < sprrenpos; ++sno)
				{
					Sprite s = OAM3[(int)sno];
					long xdiff = x - s.x;
					if(xdiff >= 8) continue;
					if((s.attr & 0x40) == 0) xdiff = 7 - xdiff;
					short spritepixel = (short)((s.pattern >> (xdiff * 2)) & 3);
					if(spritepixel == 0) continue;
					if(x < 255 && pixel != 0 && s.sprindex == 0) reg.SP0hit.set(1 /*true*/);
					if((s.attr & 0x20) == 0 || pixel == 0)
					{
						attr = (s.attr & 3) + 4;
						pixel = spritepixel;
					}

					break;
				}

			pixel = palette[(int)(((attr * 4 + pixel) & 0x1FL)&0xFFFFFFFFL)].v & (reg.Grayscale.get() != 0 ? 0x30 : 0x3F);
			IO.PutPixel(x, scanline, pixel | ((reg.EmpRGB.get() << 6)&0xFFFFFFFFL), cycle_counter);
		}

		static void tick()
		{
			switch(VBlankState)
			{
				case -5: reg.status.set(0); break;
				case 2: reg.InVBlank.set(1 /*true*/); break;
				case 0: CPU.nmi = reg.InVBlank.get() != 0 && reg.NMIenabled.get() != 0; break;
			}
			if(VBlankState != 0) VBlankState += (VBlankState < 0 ? 1 : -1);
			if(open_bus_decay_timer != 0) if(--open_bus_decay_timer == 0) open_bus = 0;

			if(scanline < 240)
			{
				if(reg.ShowBGSP.get() != 0) rendering_tick();
				if(scanline >= 0 && x < 256) render_pixel();
			}

			if(++cycle_counter == 3) cycle_counter = 0;
			if(++x >= scanline_end)
			{
				IO.FlushScanline(scanline);
				scanline_end = 341;
				x            = 0;
				switch(scanline += 1)
				{
					case 261:
						scanline = -1;
						even_odd_toggle = !even_odd_toggle;
						VBlankState = -5;
						break;
					case 241:
						// I cheat here: I did not bother to learn how to use SDL events,
						// so I simply read button presses from a movie file, which happens
						// to be a TAS, rather than from the keyboard or from a joystick.
						//TODO:
//						static FILE* fp = fopen(inputfn, "rb");
//						if(fp)
//						{
//							static unsigned ctrlmask = 0;
//							if(!ftell(fp))
//							{
//								fseek(fp, 0x05, SEEK_SET);
//								ctrlmask = fgetc(fp);
//								fseek(fp, 0x90, SEEK_SET); // Famtasia Movie format.
//							}
//							if(ctrlmask & 0x80) { IO::joy_next[0] = fgetc(fp); if(feof(fp)) IO::joy_next[0] = 0; }
//							if(ctrlmask & 0x40) { IO::joy_next[1] = fgetc(fp); if(feof(fp)) IO::joy_next[1] = 0; }
//						}

						VBlankState = 2;
				}
			}
		}
	}

	static class APU
	{
		static final short LengthCounters[] =
			{10,254,20, 2,40, 4,80, 6,160, 8,60,10,14,12,26,14, 12, 16,24,18,48,20,96,22,192,24,72,26,16,28,32,30};
		static final short NoisePeriods[] =
			{2,4,8,16,32,48,64,80,101,127,190,254,381,508,1017,2034};
		static final short DMCperiods[] =
			{428,380,340,320,286,254,226,214,190,160,142,128,106,84,72,54};

		static boolean FiveCycleDivider = false;
		static boolean IRQdisable = true;
		static boolean ChannelsEnabled[] = new boolean[5];

		static boolean PeriodicIRQ = false;
		static boolean DMC_IRQ = false;

		static Tup2<Boolean, Long> count(long v, int reset)
		{
			v = --v < 0 ? reset : v;

			return Tup2.with(v < 0, v);
		}

		static class channel
		{
			int length_counter;
			int linear_counter;
			int address;
			int envelope;

			int sweep_delay;
			int env_delay;
			int wave_counter;
			int hold;
			int phase;
			int level;

			static class reg extends RegBit
			{
				f reg0 = new f(0, 8);
				f DutyCycle = new f(6, 2);
				f EnvDecayDisable = new f(4);
				f EnvDecayRate = new f(0, 4);
				f EnvDecayLoopEnable = new f(5, 1);
				f FixedVolume = new f(0, 4);
				f LengthCounterDisable = new f(5, 1);
				f LinearCounterInit = new f(0, 7);
				f LinearCounterDisable = new f(7);

				f reg1 = new f(8, 8);
				f SweepShift = new f(8);
				f SweepDecrease = new f(11);
				f SweepRate = new f(12, 3);
				f SweepEnable = new f(15);
				f PCMlength = new f(8, 8);

				f reg2 = new f(16, 8);
				f NoiseFreq = new f(16, 4);
				f NoiseType = new f(23);
				f WaveLength = new f(16, 11);

				f reg3 = new f(24, 8);
				f LengthCounterInit = new f(27, 5);
				f LoopEnabled = new f(30);
				f IRQenable = new f(31);
			} reg reg = new reg();

			long tick(long c)
			{
				channel ch = this;
				if(!ChannelsEnabled[(int)c]) return c==4 ? 64 : 8;
				int wl = (int)(ch.reg.WaveLength.get() + 1) * (c >= 2 ? 1 : 2);
				if(c == 3) wl = NoisePeriods[(int)ch.reg.NoiseFreq.get()];
				int volume = ch.length_counter != 0 ? ch.reg.EnvDecayDisable.get() != 0 ? (int)ch.reg.FixedVolume.get() : ch.envelope : 0;
				{
					Tup2<Boolean, Long> t = count(ch.wave_counter, wl);
					ch.wave_counter = t.b.intValue();
					if(!t.a) return ch.level;
				}
				switch((int)c)
				{
					default:
						if(wl < 8) return ch.level = 8;
						return ch.level = (0xF33C0C04L & (1L << (++ch.phase % 8 + ch.reg.DutyCycle.get() * 8))) != 0 ? volume : 0;

					case 2:
						if(ch.length_counter != 0 && ch.linear_counter != 0 && wl >= 3) ++ch.phase;
						return ch.level = (ch.phase & 15) ^ ((ch.phase & 16) != 0 ? 15 : 0);

					case 3:
						if(ch.hold == 0) ch.hold = 1;
						ch.hold = (ch.hold >> 1)
							| (((ch.hold ^ (ch.hold >> (ch.reg.NoiseType.get() != 0 ? 6 : 1))) & 1) << 14);
						return ch.level = (ch.hold & 1) != 0 ? 0 : volume;

					case 4:
						if(ch.phase == 0)
						{
							if(ch.length_counter == 0 && ch.reg.LoopEnabled.get() != 0)
							{
								ch.length_counter = (int)ch.reg.PCMlength.get() * 16 + 1;
								ch.address        = (int)((ch.reg.reg0.get() | 0x300) << 6);
							}
							if(ch.length_counter > 0)
							{
								// Note: Re-entrant! But not recursive, because even
								// the shortest wave length is greater than the read time.
								// TODO: proper clock
								if(ch.reg.WaveLength.get() > 20)
									for(int t = 0; t < 3; ++t) CPU.RB(((ch.address) | 0x8000)&0xFFFF);
								ch.hold  = CPU.RB(((ch.address++) | 0x8000)&0xFFFF);
								ch.phase = 8;
								--ch.length_counter;
							}
							else
								ChannelsEnabled[4] = ch.reg.IRQenable.get() != 0 && (CPU.intr = DMC_IRQ = true);
						}
						if(ch.phase != 0)
						{
							int v = ch.linear_counter;
							if((ch.hold & (0x80 >> --ch.phase)) != 0) v += 2; else v -= 2;
							if(v >= 0 && v <= 0x7F) ch.linear_counter = v;
						}
						return ch.level = ch.linear_counter;
				}
			}
		}
		static channel channels[] = new channel[5];
		static { for(int i=0; i<channels.length; i++) channels[i]=new channel(); }

		static class hz240counter
		{
			int lo;
			int hi;
		} static hz240counter hz240counter = new hz240counter();

		static void Write(short index, short value)
		{
			channel ch = channels[(index / 4) % 5];
			switch(index < 0x10 ? index % 4 : index)
			{
				case 0: if(ch.reg.LinearCounterDisable.get() != 0) ch.linear_counter = value & 0x7F; ch.reg.reg0.set(value); break;
				case 1: ch.reg.reg1.set(value); ch.sweep_delay = (int)ch.reg.SweepRate.get(); break;
				case 2: ch.reg.reg2.set(value); break;
				case 3:
					ch.reg.reg3.set(value);
					if(ChannelsEnabled[index / 4])
						ch.length_counter = LengthCounters[(int)ch.reg.LengthCounterInit.get()];
					ch.linear_counter = (int)ch.reg.LinearCounterInit.get();
					ch.env_delay      = (int)ch.reg.EnvDecayRate.get();
					ch.envelope       = 15;
					if(index < 8) ch.phase = 0;
					break;
				case 0x10: ch.reg.reg3.set(value); ch.reg.WaveLength.set(DMCperiods[value & 0x0F]); break;
				case 0x12: ch.reg.reg0.set(value); ch.address = (int)((ch.reg.reg0.get() | 0x300) << 6); break;
				case 0x13: ch.reg.reg1.set(value); ch.length_counter = (int)(ch.reg.PCMlength.get() * 16 + 1); break;
				case 0x11: ch.linear_counter = value & 0x7F; break;
				case 0x15:
					for(int c = 0; c < 5; ++c)
						ChannelsEnabled[c] = (value & (1 << c)) != 0;
					for(int c = 0; c < 5; ++c)
						if(!ChannelsEnabled[c])
							channels[c].length_counter = 0;
						else if(c == 4 && channels[c].length_counter == 0)
							channels[c].length_counter = (int)(ch.reg.PCMlength.get() * 16 + 1);
					break;
				case 0x17:
					IRQdisable       = (value & 0x40) == 0;
					FiveCycleDivider = (value & 0x80) != 0;
					hz240counter.hi = 0; hz240counter.lo = 0;
					if(IRQdisable) PeriodicIRQ = DMC_IRQ = false;
			}
		}

		static short Read()
		{
			short res = 0;
			for(int c = 0; c < 5; ++c) res |= (channels[c].length_counter != 0 ? 1 << c : 0);
			if(PeriodicIRQ) res |= 0x40; PeriodicIRQ = false;
			if(DMC_IRQ)     res |= 0x80; DMC_IRQ     = false;
			CPU.intr = false;

			return res;
		}

		static void tick()
		{
			if((hz240counter.lo += 2) >= 14915)
			{
				hz240counter.lo -= 14915;
				if(++hz240counter.hi >= 4 + (FiveCycleDivider ? 1 : 0)) hz240counter.hi = 0;

				if(!IRQdisable && !FiveCycleDivider && hz240counter.hi==0)
					CPU.intr = PeriodicIRQ = true;

				boolean HalfTick = (hz240counter.hi & 5) == 1, FullTick = hz240counter.hi < 4;
				for(int c = 0; c < 4; ++c)
				{
					channel ch = channels[c];
					int wl = (int)ch.reg.WaveLength.get();

					if(HalfTick
							&& ch.length_counter != 0
							&& (c == 2 ? ch.reg.LinearCounterDisable.get() : ch.reg.LengthCounterDisable.get()) == 0)
						ch.length_counter -= 1;

					Tup2<Boolean, Long> t = count(ch.sweep_delay, (int)ch.reg.SweepRate.get());
					ch.sweep_delay = t.b.intValue();
					if(HalfTick && c < 2 && t.a)
						if(wl >= 8 && ch.reg.SweepEnable.get() != 0 && ch.reg.SweepShift.get() != 0)
						{
							int s = wl >> ch.reg.SweepShift.get();
							//TODO: ???
//							d[4] = {s, s, ~s, -s};
//							wl += d[(int)ch.reg.SweepDecrease.get() * 2 + c];
							if(wl < 0x800) ch.reg.WaveLength.set(wl);
						}

					if(FullTick && c == 2)
						ch.linear_counter = ch.reg.LinearCounterDisable.get() != 0
							? (int)ch.reg.LinearCounterInit.get()
							: (ch.linear_counter > 0 ? ch.linear_counter - 1 : 0);

					Tup2<Boolean, Long> t2 = count(ch.env_delay, (int)ch.reg.EnvDecayRate.get());
					ch.env_delay = t2.b.intValue();
					if(FullTick && c != 2 && t2.a)
						if(ch.envelope > 0 || ch.reg.EnvDecayLoopEnable.get() != 0)
							ch.envelope = (ch.envelope-1) & 15;
				}
			}

			//TODO:
//			#define s(c) channels[c].tick < c == 1 ? 0 : c > () // Wat?
//			auto v = [](float m,float n, float d) { return n!=0.f ? m/n : d; };
//			short sample = 30000 *
//				(v(95.88f,  (100.f + v(8128.f, s(0) + s(1), -100.f)), 0.f)
//					+  v(159.79f, (100.f + v(1.0, s(2)/8227.f + s(3)/12241.f + s(4)/22638.f, -100.f)), 0.f)
//					- 0.5f
//				);

			// I cheat here: I did not bother to learn how to use SDL mixer, let alone use it in <5 lines of code,
			// so I simply use a combination of external programs for outputting the audio.
			// Hooray for Unix principles! A/V sync will be ensured in post-process.
			//return; // Disable sound because already device is in use
			//TODO:
			return;
//			static FILE* fp = popen("resample mr1789800 r48000 | aplay -fdat 2>/dev/null", "w");
//			fputc(sample, fp);
//			fputc(sample/256, fp);
		}
	}

	static class CPU
	{
		static short RAM[] = new short[0x800];
		static boolean  reset = true
		               ,nmi = false
		               ,nmi_edge_detected = false
		               ,intr = false
		               ;

		static short RB(int addr)      { return MemAccess(addr, (short)0/*wat?*/, false); }
		static short WB(int addr, short v) { return MemAccess(addr, v, true); }

		static void tick()
		{
			// PPU clock: 3 times the CPU rate
			for(int n = 0; n < 3; ++n) PPU.tick();
			// APU clock: 1 times the CPU rate
			for(int n = 0; n < 1; ++n) APU.tick();
		}

		static short MemAccess(int addr, short v, final boolean write)
		{
			if(reset && write) return MemAccess(addr, (short)0 /*wat?*/, false);

			tick();
			     if(addr < 0x2000) { short r = RAM[addr & 0x7FF]; if(!write) return r; RAM[addr & 0x7FF] = v; }
			else if(addr < 0x4000) return (short)PPU.Access(addr & 7, v, write);
			else if(addr < 0x4018)
				switch(addr & 0x1F)
				{
					case 0x14:
						if(write) for(int b = 0; b < 256; ++b) WB(0x2004, RB((v&7)*0x0100+b));
						return 0;
					case 0x15: if(!write) return APU.Read(); APU.Write((short)0x15, v); break;
					case 0x16: if(!write) return IO.JoyRead(0); IO.JoyStrobe(v); break;
					case 0x17: if(!write) return IO.JoyRead(1);
					default: if(!write) break;
									 APU.Write((short)(addr&0x1F), v);
				}
			else
				return GamePak.Access(addr, v, write);

			return 0;
		}

		static int PC = 0xC000;
		static short A = 0;
		static short X = 0;
		static short Y = 0;
		static short S = 0;

		static class P extends RegBit
		{
			f C = new f(0);
			f Z = new f(1);
			f I = new f(2);
			f D = new f(3);
			f V = new f(6);
			f N = new f(7);
		} static P P = new P();

		static int wrap(int oldaddr, int newaddr)
		{
			return (oldaddr & 0xFF00) + (newaddr&0xFF);
		}

		static void Misfire(int old, int addr)
		{
			int q = wrap(old, addr);
			if(q != addr) RB(q);
		}

		static short Pop()
		{
			return (short)(RB(0x100|((++S)&0xFF))&0xFF);
		}

		static void Push(short v)
		{
			WB(0x100 | ((S--)&0xFF), v);
		}

		static int ij = 0;
		static void Op()
		{
			boolean nmi_now = nmi;

			int op = RB(PC++);

			if(reset)                              { op = 0x101; }
			else if(nmi_now && !nmi_edge_detected) { op = 0x100; nmi_edge_detected = true; }
			else if(intr && P.I.get() == 0)        { op = 0x102; }
			if(!nmi_now) nmi_edge_detected = false;

			//System.out.println((ij++)+": "+op+": "+P.data+" "+PC+" "+(S&0xFF)+" "+PPU.reg.data+" "+PPU.scroll.data+ " "+PPU.vaddr.data);
			switch(op)
			{
				case 0: op0(); break;
				case 1: op1(); break;
				case 2: op2(); break;
				case 3: op3(); break;
				case 4: op4(); break;
				case 5: op5(); break;
				case 6: op6(); break;
				case 7: op7(); break;
				case 8: op8(); break;
				case 9: op9(); break;
				case 10: op10(); break;
				case 11: op11(); break;
				case 12: op12(); break;
				case 13: op13(); break;
				case 14: op14(); break;
				case 15: op15(); break;
				case 16: op16(); break;
				case 17: op17(); break;
				case 18: op18(); break;
				case 19: op19(); break;
				case 20: op20(); break;
				case 21: op21(); break;
				case 22: op22(); break;
				case 23: op23(); break;
				case 24: op24(); break;
				case 25: op25(); break;
				case 26: op26(); break;
				case 27: op27(); break;
				case 28: op28(); break;
				case 29: op29(); break;
				case 30: op30(); break;
				case 31: op31(); break;
				case 32: op32(); break;
				case 33: op33(); break;
				case 34: op34(); break;
				case 35: op35(); break;
				case 36: op36(); break;
				case 37: op37(); break;
				case 38: op38(); break;
				case 39: op39(); break;
				case 40: op40(); break;
				case 41: op41(); break;
				case 42: op42(); break;
				case 43: op43(); break;
				case 44: op44(); break;
				case 45: op45(); break;
				case 46: op46(); break;
				case 47: op47(); break;
				case 48: op48(); break;
				case 49: op49(); break;
				case 50: op50(); break;
				case 51: op51(); break;
				case 52: op52(); break;
				case 53: op53(); break;
				case 54: op54(); break;
				case 55: op55(); break;
				case 56: op56(); break;
				case 57: op57(); break;
				case 58: op58(); break;
				case 59: op59(); break;
				case 60: op60(); break;
				case 61: op61(); break;
				case 62: op62(); break;
				case 63: op63(); break;
				case 64: op64(); break;
				case 65: op65(); break;
				case 66: op66(); break;
				case 67: op67(); break;
				case 68: op68(); break;
				case 69: op69(); break;
				case 70: op70(); break;
				case 71: op71(); break;
				case 72: op72(); break;
				case 73: op73(); break;
				case 74: op74(); break;
				case 75: op75(); break;
				case 76: op76(); break;
				case 77: op77(); break;
				case 78: op78(); break;
				case 79: op79(); break;
				case 80: op80(); break;
				case 81: op81(); break;
				case 82: op82(); break;
				case 83: op83(); break;
				case 84: op84(); break;
				case 85: op85(); break;
				case 86: op86(); break;
				case 87: op87(); break;
				case 88: op88(); break;
				case 89: op89(); break;
				case 90: op90(); break;
				case 91: op91(); break;
				case 92: op92(); break;
				case 93: op93(); break;
				case 94: op94(); break;
				case 95: op95(); break;
				case 96: op96(); break;
				case 97: op97(); break;
				case 98: op98(); break;
				case 99: op99(); break;
				case 100: op100(); break;
				case 101: op101(); break;
				case 102: op102(); break;
				case 103: op103(); break;
				case 104: op104(); break;
				case 105: op105(); break;
				case 106: op106(); break;
				case 107: op107(); break;
				case 108: op108(); break;
				case 109: op109(); break;
				case 110: op110(); break;
				case 111: op111(); break;
				case 112: op112(); break;
				case 113: op113(); break;
				case 114: op114(); break;
				case 115: op115(); break;
				case 116: op116(); break;
				case 117: op117(); break;
				case 118: op118(); break;
				case 119: op119(); break;
				case 120: op120(); break;
				case 121: op121(); break;
				case 122: op122(); break;
				case 123: op123(); break;
				case 124: op124(); break;
				case 125: op125(); break;
				case 126: op126(); break;
				case 127: op127(); break;
				case 128: op128(); break;
				case 129: op129(); break;
				case 130: op130(); break;
				case 131: op131(); break;
				case 132: op132(); break;
				case 133: op133(); break;
				case 134: op134(); break;
				case 135: op135(); break;
				case 136: op136(); break;
				case 137: op137(); break;
				case 138: op138(); break;
				case 139: op139(); break;
				case 140: op140(); break;
				case 141: op141(); break;
				case 142: op142(); break;
				case 143: op143(); break;
				case 144: op144(); break;
				case 145: op145(); break;
				case 146: op146(); break;
				case 147: op147(); break;
				case 148: op148(); break;
				case 149: op149(); break;
				case 150: op150(); break;
				case 151: op151(); break;
				case 152: op152(); break;
				case 153: op153(); break;
				case 154: op154(); break;
				case 155: op155(); break;
				case 156: op156(); break;
				case 157: op157(); break;
				case 158: op158(); break;
				case 159: op159(); break;
				case 160: op160(); break;
				case 161: op161(); break;
				case 162: op162(); break;
				case 163: op163(); break;
				case 164: op164(); break;
				case 165: op165(); break;
				case 166: op166(); break;
				case 167: op167(); break;
				case 168: op168(); break;
				case 169: op169(); break;
				case 170: op170(); break;
				case 171: op171(); break;
				case 172: op172(); break;
				case 173: op173(); break;
				case 174: op174(); break;
				case 175: op175(); break;
				case 176: op176(); break;
				case 177: op177(); break;
				case 178: op178(); break;
				case 179: op179(); break;
				case 180: op180(); break;
				case 181: op181(); break;
				case 182: op182(); break;
				case 183: op183(); break;
				case 184: op184(); break;
				case 185: op185(); break;
				case 186: op186(); break;
				case 187: op187(); break;
				case 188: op188(); break;
				case 189: op189(); break;
				case 190: op190(); break;
				case 191: op191(); break;
				case 192: op192(); break;
				case 193: op193(); break;
				case 194: op194(); break;
				case 195: op195(); break;
				case 196: op196(); break;
				case 197: op197(); break;
				case 198: op198(); break;
				case 199: op199(); break;
				case 200: op200(); break;
				case 201: op201(); break;
				case 202: op202(); break;
				case 203: op203(); break;
				case 204: op204(); break;
				case 205: op205(); break;
				case 206: op206(); break;
				case 207: op207(); break;
				case 208: op208(); break;
				case 209: op209(); break;
				case 210: op210(); break;
				case 211: op211(); break;
				case 212: op212(); break;
				case 213: op213(); break;
				case 214: op214(); break;
				case 215: op215(); break;
				case 216: op216(); break;
				case 217: op217(); break;
				case 218: op218(); break;
				case 219: op219(); break;
				case 220: op220(); break;
				case 221: op221(); break;
				case 222: op222(); break;
				case 223: op223(); break;
				case 224: op224(); break;
				case 225: op225(); break;
				case 226: op226(); break;
				case 227: op227(); break;
				case 228: op228(); break;
				case 229: op229(); break;
				case 230: op230(); break;
				case 231: op231(); break;
				case 232: op232(); break;
				case 233: op233(); break;
				case 234: op234(); break;
				case 235: op235(); break;
				case 236: op236(); break;
				case 237: op237(); break;
				case 238: op238(); break;
				case 239: op239(); break;
				case 240: op240(); break;
				case 241: op241(); break;
				case 242: op242(); break;
				case 243: op243(); break;
				case 244: op244(); break;
				case 245: op245(); break;
				case 246: op246(); break;
				case 247: op247(); break;
				case 248: op248(); break;
				case 249: op249(); break;
				case 250: op250(); break;
				case 251: op251(); break;
				case 252: op252(); break;
				case 253: op253(); break;
				case 254: op254(); break;
				case 255: op255(); break;
				case 256: op256(); break;
				case 257: op257(); break;
				case 258: op258(); break;
				case 259: op259(); break;
				case 260: op260(); break;
				case 261: op261(); break;
				case 262: op262(); break;
				case 263: op263(); break;
				default: System.err.println("ERR: Unknown op " + op + ".");
			}

			reset = false;
		}

		/*
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
			----------------------------------------
		*/

		static void op0()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = 0xFFFE;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			d=PC+(0!=0?-1:1); Push((short)((d>>8)&0xFFFF)); Push((short)(d&0xFFFF));
			PC = addr;
			Push((short)(t&0xFFFF));
			t = 1;
			t <<= 2;
			t = c | t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op1()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op2()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op3()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op4()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			tick();
		}

		static void op5()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op6()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op7()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op8()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			Push((short)(t&0xFFFF));
		}

		static void op9()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			c = t; t = 0xFF;
			t &= RB(PC++);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op10()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			tick();
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op11()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			P.C.set((t & 0x80)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op12()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			tick();
		}

		static void op13()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op14()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op15()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op16()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t <<= 1;
			t <<= 2;
			t <<= 4;
			t = c & t;
			if(t == 0) { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op17()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op18()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op19()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op20()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			tick();
		}

		static void op21()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op22()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op23()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op24()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			t = 1;
			t = (~t)&0xFF;
			t = c & t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op25()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op26()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
		}

		static void op27()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op28()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			tick();
		}

		static void op29()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op30()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op31()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c | t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op32()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			tick();
			d=PC+(32!=0?-1:1); Push((short)((d>>8)&0xFFFF)); Push((short)(d&0xFFFF));
			PC = addr;
		}

		static void op33()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op34()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op35()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op36()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.V.set((t & 0x40)&0xFF); P.N.set((t & 0x80)&0xFF);
			t = c & t;
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op37()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op38()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op39()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op40()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
			tick(); t = Pop();
			P.data = (t & ~0x30)&0xFF;
		}

		static void op41()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			c = t; t = 0xFF;
			t &= RB(PC++);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op42()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			tick();
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op43()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			P.C.set((t & 0x80)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op44()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.V.set((t & 0x40)&0xFF); P.N.set((t & 0x80)&0xFF);
			t = c & t;
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op45()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op46()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op47()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op48()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t <<= 1;
			t <<= 2;
			t <<= 4;
			t = c & t;
			if(t != 0)  { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op49()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op50()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op51()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op52()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			tick();
		}

		static void op53()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op54()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op55()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op56()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			t = 1;
			t = c | t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op57()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op58()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
		}

		static void op59()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op60()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			tick();
		}

		static void op61()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op62()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op63()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t << 1) | (sb * 0x01);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c & t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op64()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick(); t = Pop();
			RB(PC++); PC = Pop(); PC |= (Pop() << 8);
			P.data = (t & ~0x30)&0xFF;
		}

		static void op65()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op66()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op67()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op68()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			tick();
		}

		static void op69()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op70()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op71()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op72()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			tick();
			Push((short)(t&0xFFFF));
		}

		static void op73()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			c = t; t = 0xFF;
			t &= RB(PC++);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op74()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			tick();
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op75()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op76()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			PC = addr;
		}

		static void op77()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op78()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op79()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op80()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t <<= 2;
			t <<= 4;
			t = c & t;
			if(t == 0) { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op81()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op82()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op83()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op84()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			tick();
		}

		static void op85()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op86()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op87()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op88()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			t = 1;
			t <<= 2;
			t = (~t)&0xFF;
			t = c & t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op89()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op90()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
		}

		static void op91()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op92()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			tick();
		}

		static void op93()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op94()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op95()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c ^ t;
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op96()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
			RB(PC++); PC = Pop(); PC |= (Pop() << 8);
			RB(PC++);
		}

		static void op97()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= RB(addr+d);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op98()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op99()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op100()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			tick();
		}

		static void op101()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op102()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op103()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op104()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
			tick(); t = Pop();
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op105()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op106()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			tick();
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op107()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x80)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
			P.V.set(((((t >> 5)+1)&2)&0xFF));
		}

		static void op108()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			PC = addr;
		}

		static void op109()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op110()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op111()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op112()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t <<= 2;
			t <<= 4;
			t = c & t;
			if(t != 0)  { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op113()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op114()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op115()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op116()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			tick();
		}

		static void op117()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op118()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op119()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op120()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			t = 1;
			t <<= 2;
			t = c | t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op121()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op122()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
		}

		static void op123()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op124()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			tick();
		}

		static void op125()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op126()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op127()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			sb = (int)(P.C.get()&0xFF);
			P.C.set((t & 0x01)&0xFF);
			t = (t >> 1) | (sb * 0x80);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op128()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= Y;
			t &= RB(PC++);
		}

		static void op129()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op130()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
		}

		static void op131()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op132()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= Y;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op133()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op134()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op135()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op136()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= Y;
			t = ((t - 1)&0xFF);
			tick();
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op137()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= RB(PC++);
		}

		static void op138()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= X;
			tick();
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op139()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= X;
			t &= RB(PC++);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op140()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= Y;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op141()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op142()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op143()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op144()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t = c & t;
			if(t == 0) { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op145()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= A;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op146()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op147()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= A;
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op148()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= Y;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op149()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op150()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op151()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			t &= X;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op152()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= Y;
			tick();
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op153()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op154()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= X;
			tick();
			S = (short)t;
		}

		static void op155()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			t &= X;
			WB(wrap(addr, addr+d), (short)((t &= ((addr+d) >> 8))&0xFFFF));
			S = (short)t;
		}

		static void op156()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= Y;
			WB(wrap(addr, addr+d), (short)((t &= ((addr+d) >> 8))&0xFFFF));
		}

		static void op157()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			WB(addr+d, (short)(t&0xFFFF));
		}

		static void op158()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= X;
			WB(wrap(addr, addr+d), (short)((t &= ((addr+d) >> 8))&0xFFFF));
		}

		static void op159()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			t &= X;
			WB(wrap(addr, addr+d), (short)((t &= ((addr+d) >> 8))&0xFFFF));
		}

		static void op160()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op161()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= RB(addr+d);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op162()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op163()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= RB(addr+d);
			A = (short)t;
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op164()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op165()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op166()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op167()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			A = (short)t;
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op168()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			tick();
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op169()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op170()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			tick();
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op171()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
			A = (short)t;
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op172()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op173()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op174()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op175()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			A = (short)t;
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op176()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t = c & t;
			if(t != 0)  { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op177()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op178()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op179()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			A = (short)t;
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op180()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op181()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op182()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op183()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			A = (short)t;
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op184()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			t = 1;
			t <<= 2;
			t <<= 4;
			t = (~t)&0xFF;
			t = c & t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op185()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op186()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= S;
			tick();
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op187()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= S;
			t &= RB(addr+d);
			A = (short)t;
			X = (short)t;
			S = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op188()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op189()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op190()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op191()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			A = (short)t;
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op192()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= Y;
			c = t; t = 0xFF;
			t &= RB(PC++);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op193()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op194()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
		}

		static void op195()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op196()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= Y;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op197()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op198()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op199()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op200()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= Y;
			t = ((t + 1)&0xFF);
			tick();
			Y = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op201()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			c = t; t = 0xFF;
			t &= RB(PC++);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op202()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= X;
			t = ((t - 1)&0xFF);
			tick();
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op203()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= A;
			t &= X;
			c = t; t = 0xFF;
			t &= RB(PC++);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op204()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= Y;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op205()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op206()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op207()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op208()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t <<= 1;
			t = c & t;
			if(t == 0) { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op209()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op210()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op211()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op212()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			tick();
		}

		static void op213()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op214()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op215()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op216()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			t = 1;
			t <<= 1;
			t <<= 2;
			t = (~t)&0xFF;
			t = c & t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op217()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op218()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
		}

		static void op219()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op220()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			tick();
		}

		static void op221()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op222()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op223()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= A;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = ((t - 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op224()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= X;
			c = t; t = 0xFF;
			t &= RB(PC++);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op225()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= RB(addr+d);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op226()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
		}

		static void op227()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op228()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= X;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op229()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op230()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op231()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op232()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= X;
			t = ((t + 1)&0xFF);
			tick();
			X = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op233()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op234()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
		}

		static void op235()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= RB(PC++);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op236()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= X;
			c = t; t = 0xFF;
			t &= RB(addr+d);
			t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL);
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op237()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op238()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op239()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op240()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			t &= (P.data&0xFF)|pbits; c = t;
			t = 1;
			t <<= 1;
			t = c & t;
			if(t != 0)  { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; };
		}

		static void op241()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op242()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op243()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op244()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			tick();
		}

		static void op245()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op246()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op247()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=((addr+d)&0xFF); d=0; tick();
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op248()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			t = 1;
			t <<= 1;
			t <<= 2;
			t = c | t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op249()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op250()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			tick();
		}

		static void op251()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = Y;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op252()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			tick();
		}

		static void op253()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			Misfire(addr, addr+d);
			t &= RB(addr+d);
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op254()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op255()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 48;
			addr = RB(PC++);
			d = X;
			addr=(addr&0xFF);   addr+=256*RB(PC++);
			RB(wrap(addr, addr+d));
			t &= RB(addr+d);
			t = ((t + 1)&0xFF);
			WB(addr+d, (short)(t&0xFFFF));
			tick();
			t = (~t)&0xFF;
			c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF);
			A = (short)t;
			P.N.set((t & 0x80)&0xFF);
			P.Z.set((t&0xFF) == 0 ? 1 : 0);
		}

		static void op256()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
			addr = 0xFFFA;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			d=PC+(256!=0?-1:1); Push((short)((d>>8)&0xFFFF)); Push((short)(d&0xFFFF));
			PC = addr;
			Push((short)(t&0xFFFF));
			t = 1;
			t <<= 2;
			t = c | t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op257()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
			addr = 0xFFFC;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			d=PC+(257!=0?-1:1); Push((short)((d>>8)&0xFFFF)); Push((short)(d&0xFFFF));
			PC = addr;
			Push((short)(t&0xFFFF));
			t = 1;
			t <<= 2;
			t = c | t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op258()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
			addr = 0xFFFE;
			addr=RB(c=addr); addr+=256*RB(wrap(c,c+1));
			t &= (P.data&0xFF)|pbits; c = t;
			tick();
			d=PC+(258!=0?-1:1); Push((short)((d>>8)&0xFFFF)); Push((short)(d&0xFFFF));
			PC = addr;
			Push((short)(t&0xFFFF));
			t = 1;
			t <<= 2;
			t = c | t;
			P.data = (t & ~0x30)&0xFF;
		}

		static void op259()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
		}

		static void op260()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
		}

		static void op261()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
		}

		static void op262()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
		}

		static void op263()
		{
			int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = 32;
		}
	}
}

