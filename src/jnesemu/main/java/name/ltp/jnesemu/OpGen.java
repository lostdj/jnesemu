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

public class OpGen
{
	static final mc[] mc =
	{
		 new mc("                                !", "addr = 0xFFFA")
		,new mc("                                *", "addr = 0xFFFC")
		,new mc("!                               ,", "addr = 0xFFFE")
		,new mc("zy}z{y}zzy}zzy}zzy}zzy}zzy}zzy}z ", "addr = RB(PC++)")
		,new mc("2 yy2 yy2 yy2 yy2 XX2 XX2 yy2 yy ", "d = X")
		,new mc("  62  62  62  62  om  om  62  62 ", "d = Y")
		,new mc("2 y 2 y 2 y 2 y 2 y 2 y 2 y 2 y  ", "addr=((addr+d)&0xFF); d=0; tick()")
		,new mc(" y z!y z y z y z y z y z y z y z ", "addr=(addr&0xFF);   addr+=256*RB(PC++)")
		,new mc("3 6 2 6 2 6 286 2 6 2 6 2 6 2 6 /", "addr=RB(c=addr); addr+=256*RB(wrap(c,c+1))")
		,new mc("  *Z  *Z  *Z  *Z      6z  *Z  *Z ", "Misfire(addr, addr+d)")
		,new mc("  4k  4k  4k  4k  6z      4k  4k ", "RB(wrap(addr, addr+d))")
		,new mc("aa__ff__ab__,4  ____ -  ____     ", "t &= A")
		,new mc("                knnn     4  99   ", "t &= X")
		,new mc("                9989    99       ", "t &= Y")
		,new mc("                       4         ", "t &= S")
		,new mc("!!!!  !!  !!  !!  !   !!  !!  !!/", "t &= (P.data&0xFF)|pbits; c = t")
		,new mc("_^__dc___^__            ed__98   ", "c = t; t = 0xFF")
		,new mc("vuwvzywvvuwvvuwv    zy|zzywvzywv ", "t &= RB(addr+d)")
		,new mc(",2  ,2  ,2  ,2  -2  -2  -2  -2   ", "t &= RB(PC++)")
		,new mc("    88                           ", "P.V.set((t & 0x40)&0xFF); P.N.set((t & 0x80)&0xFF)")
		,new mc("    nink    nnnk                 ", "sb = (int)(P.C.get()&0xFF)")
		,new mc("nnnknnnk     0                   ", "P.C.set((t & 0x80)&0xFF)")
		,new mc("        nnnknink                 ", "P.C.set((t & 0x01)&0xFF)")
		,new mc("ninknink                         ", "t = (t << 1) | (sb * 0x01)")
		,new mc("        nnnknnnk                 ", "t = (t >> 1) | (sb * 0x80)")
		,new mc("                 !      kink     ", "t = ((t - 1)&0xFF)")
		,new mc("                         !  khnk ", "t = ((t + 1)&0xFF)")
		,new mc("kgnkkgnkkgnkkgnkzy|J    kgnkkgnk ", "WB(addr+d, (short)(t&0xFFFF))")
		,new mc("                   q             ", "WB(wrap(addr, addr+d), (short)((t &= ((addr+d) >> 8))&0xFFFF))")
		,new mc("rpstljstqjstrjst - - - -kjstkjst/", "tick()")
		,new mc("     !  !    !                   ", "tick(); t = Pop()")
		,new mc("        !   !                    ", "RB(PC++); PC = Pop(); PC |= (Pop() << 8)")
		,new mc("            !                    ", "RB(PC++)")
		,new mc("!   !                           /", "d=PC+(%OP%!=0?-1:1); Push((short)((d>>8)&0xFFFF)); Push((short)(d&0xFFFF))")
		,new mc("!   !    8   8                  /", "PC = addr")
		,new mc("!!       !                      /", "Push((short)(t&0xFFFF))")
		,new mc("! !!  !!  !!  !!  !   !!  !!  !!/", "t = 1")
		,new mc("  !   !                   !!  !! ", "t <<= 1")
		,new mc("! !   !   !!  !!       !   !   !/", "t <<= 2")
		,new mc("  !   !   !   !        !         ", "t <<= 4")
		,new mc("   !       !           !   !____ ", "t = (~t)&0xFF")
		,new mc("`^__   !       !               !/", "t = c | t")
		,new mc("  !!dc`_  !!  !   !   !!  !!  !  ", "t = c & t")
		,new mc("        _^__                     ", "t = c ^ t")
		,new mc("      !       !       !       !  ", "if(t != 0)  { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; }")
		,new mc("  !       !       !       !      ", "if(t == 0) { tick(); Misfire(PC, addr = ((byte)addr) + PC); PC=addr; }")
		,new mc("            _^__            ____ ", "c = t; t += A + (P.C.get()&0xFF); P.V.set(((c^t) & (A^t) & 0x80)&0xFF); P.C.set((t & 0x100)&0xFF)")
		,new mc("                        ed__98   ", "t = c - t; P.C.set((~t & 0x100)&0xFFFFFFFFL)")
		,new mc("aa__aa__aa__ab__ 4 !____    ____ ", "A = (short)t")
		,new mc("                    nnnn 4   !   ", "X = (short)t")
		,new mc("                 !  9988 !       ", "Y = (short)t")
		,new mc("                   4   0         ", "S = (short)t")
		,new mc("!  ! ! !!  !   !       !   !   !/", "P.data = (t & ~0x30)&0xFF")
		,new mc("wwwvwwwvwwwvwxwv 5 !}}||{}wv{{wv ", "P.N.set((t & 0x80)&0xFF)")
		,new mc("wwwv||wvwwwvwxwv 5 !}}||{}wv{{wv ", "P.Z.set((t&0xFF) == 0 ? 1 : 0)")
		,new mc("             0                   ", "P.V.set(((((t >> 5)+1)&2)&0xFF))")
	};

	static class mc
	{
		final String s;
		final String c;

		mc(String s, String c)
		{
			this.s = s;
			this.c = c;
		}
	}

	static void ins(int op)
	{
		System.out.println("static void op" + op + "()");
		System.out.println("{");
		System.out.println("int addr=0, d=0, t=0xFF, c=0, sb=0, pbits = " + (op<0x100 ? 0x30 : 0x20) + ";");

		final int o8 = op / 8;
		final int o8m = 1 << (op%8);

//		System.out.print(op+": ");

		for(mc m : mc)
		{
			int j =
				(o8m &
				 (m.s.charAt(o8) > 90
					? 130+" (),-089<>?BCFGHJLSVWZ[^hlmnxy|}".charAt(m.s.charAt(o8)-94)
					: m.s.charAt(o8)-" ((".charAt(m.s.charAt(o8)/39)));
			boolean i = j != 0;

//			System.out.print(j+" | ");

			if(i)
				System.out.println(m.c.replaceAll("%OP%", op+"") + ";");
		}

//		System.out.print("\n\n----------------------------------------\n\n");

		System.out.println("}");
		System.out.println();
	}

	static void c(int n)
	{
		ins(n); ins(n+1);

//		System.out.println("case " + n + ": op" + n + "(); break;");
//		System.out.println("case " + (n+1) + ": op" + (n+1) + "(); break;");
	}

	static void o(int n)
	{
		c(n); c(n+2); c(n+4); c(n+6);
	}

	public static void main(String[] args)
	{
		System.out.println("switch(op)");
		System.out.println("{");

		o(0x00);o(0x08);o(0x10);o(0x18);o(0x20);o(0x28);o(0x30);o(0x38);
		o(0x40);o(0x48);o(0x50);o(0x58);o(0x60);o(0x68);o(0x70);o(0x78);
		o(0x80);o(0x88);o(0x90);o(0x98);o(0xA0);o(0xA8);o(0xB0);o(0xB8);
		o(0xC0);o(0xC8);o(0xD0);o(0xD8);o(0xE0);o(0xE8);o(0xF0);o(0xF8); o(0x100);

		System.out.println("default: System.err.println(\"ERR: Unknown op \" + op + \".\");");
		System.out.println("}");
		System.out.println("");
	}
}
