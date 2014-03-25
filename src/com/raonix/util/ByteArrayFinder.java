package com.raonix.util;

public class ByteArrayFinder
{
	private ByteArrayFinder() {} // static class

	/**
	 * 바이트 어레이에서 match내용을 찾기
	 * @param source 찾을곳
	 * @param match 찾을것
	 * @return match가 시작되는 바이트. ==-1 의경우 찾지못함.
	 */
	public static int find(final byte[] source, final byte[] match)
	{
		// sanity checks
		if(source == null || match == null)
			return -1;
		if(source.length == 0 || match.length == 0)
			return -1;

		int ret = -1;
		int spos = 0;
		int mpos = 0;
		byte m = match[mpos];

		for( ; spos < source.length; spos++ )
		{
			if(m == source[spos])
			{
				// starting match
				if(mpos == 0)
					ret = spos;
				// finishing match
				else if(mpos == match.length - 1)
					return ret;
				mpos++;
				m = match[mpos];
			}
			else
			{
				//FIXME:match내에 같은 내용이 있을경우 찾지 못할 수 있슴.
				ret = -1;
				mpos = 0;
				m = match[mpos];
			}
		}
		return ret;
	}
}
