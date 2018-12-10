package jackwtat.simplembta.adapters;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * Created by jackw on 8/21/2017.
 */

public class FragmentsPagerAdapter extends FragmentPagerAdapter {
    private Fragment[] fragments;
    private String[] tabTitles;

    public FragmentsPagerAdapter(FragmentManager fm, Fragment[] fragments, String[] tabTitles) {
        super(fm);

        this.fragments = fragments;
        this.tabTitles = tabTitles;
    }

    @Override
    public Fragment getItem(int position) {
        return fragments[position];
    }

    @Override
    public int getCount() {
        return fragments.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (position < tabTitles.length)
            return tabTitles[position];
        else
            return "null";
    }


}
