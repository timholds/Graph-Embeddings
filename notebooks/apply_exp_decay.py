import pandas as pd
import numpy as np

file_name = 'Score_TitleField_166M_1900-2020-5.csv'
root = '/Users/timholdsworth/code/scaling-science'

def get_data(file_name):
    path_in = root + '/Data/'+ file_name
    df = pd.read_csv(path_in, encoding='latin1')
    return df

# Add a column thats sums up all the values in a row, which are all the paper's pagerank scores in given years
def add_score_sum(df):
    df['score_sum'] = df.sum(axis=1, skipna=True)
    return df

# Add a column with the number of years since the paper was published
def add_total_years_pub(df):
    df['total_years_pub'] = len(df.columns) - 2 - df.isnull().sum(axis=1, skipna=True)
    return df

# Add a column with the average score for a paper
def add_time_weighted_score(df):
    df['time_weighted_score'] = df['score_sum'] / df['total_years_pub']
    return df

# Sort by the most popular papers according to time weighted score
def sort(df):
    df = df.sort_values(by=['time_weighted_score'], ascending=False).reset_index(drop=True)
    return df

num_results = 100
def clean_for_viz(df, num_results):
    df = df.head(num_results)
    df = df.round(3)
    df = df.rename(columns={"a.title": "title"})
    return df


# Returns a series of len(df) where each value is the column the data starts in for each row
def get_total_years_pub(df):
    total_years_pub = df.loc[:, 'total_years_pub']
    # Because there are 3 extra column at the end
    start_col_pos = total_years_pub + 3
    # Because we want the last certain number of columns
    start_cols = -start_col_pos

    return start_cols


# Method to calculate decay_scores for a given paper, returning the scores as a series
def calc_decay_scores(df, start_col, index, decay_rate):
    # Get the nondecayed scores
    start = start_col[index]
    impact_scores_unlogged = df.iloc[start:-3, index].reset_index(drop=True)
    impact_scores = impact_scores_unlogged.astype(float).apply(np.log)
    # Generate a series of decay coefficients
    time = np.arange(len(impact_scores))
    decay_list = [np.exp(-t / decay_rate) for t in time]
    decay_series = pd.Series(decay_list)

    # Multiply the decay coefficeints by the nondecayed scores
    decay_score = decay_series.multiply(impact_scores)

    return decay_score


# Method to update the dataframe with the impact scores
def update_df_with_decay_scores(df, start_cols, decay_rate, year_step):
    # Get the index at which data starts for a given column
    total_years_pub = df.loc[:, 'total_years_pub']
    start_index = total_years_pub + 3

    # Set the index to paper title and transpose main df
    df = df.set_index('title')
    df = df.transpose()

    count = 0

    # For all papers, where each column represents a paper, update the score with the decayed_score
    for column in df:
        # Calculate the decay scores for each row
        decay_score = calc_decay_scores(df, start_cols, df.columns.get_loc(column), decay_rate)

        # Turn the decayed_score into a df with column names matching and back to year-indexed series
        decay_frame = decay_score.to_frame()

        # Get the value of the column title from the dataframe itself - which is the column title
        decay_frame.columns = [list(df.columns.values)[df.columns.get_loc(column)]]

        # Build an index of years for the decay_frame
        time = np.arange(len(decay_score))
        year_index = start_index[count]
        years = df.index.values.tolist()
        year = years[-year_index]
        year_list = ['' + str((int(year)) + year_step * t) + '' for t in time]
        year_series = pd.Series(year_list)
        decay_frame['years'] = year_series
        decay_frame = decay_frame.set_index('years').round(2)

        # Update the dataframe with the new values
        df.update(decay_frame)

        count = count + 1

    df = df.round(3)
    return df.T

def write_to_csv(df, decay_rate):
    path_out = root + '/Data/' + file_name[:-4] + '_' + str(
        num_results) + '_results_decayed_at_' + str(decay_rate) + '.csv'
    df1 = df.round(3)
    df1.to_csv(path_out, index_label='title')

# Takes in data, finds most impactful papers, applies decay scores, writes these to csv
def data_prep(file_name, num_results):
    df = get_data(file_name)
    df1 = add_score_sum(df)
    df2 = add_total_years_pub(df1)
    df3 = add_time_weighted_score(df2)
    df4 = sort(df3)
    df5 = clean_for_viz(df4, num_results)
    return df5

def update(df, decay_rate, year_step):
    start_cols = get_total_years_pub(df)
    df1 = update_df_with_decay_scores(df, start_cols, decay_rate, year_step)
    write_to_csv(df1, decay_rate)

def main(filename=None, num_results = None, year_step=None, decay_rate=None):

    if filename is None:
        filename = file_name
    if num_results is None:
        num_results = 100
    if year_step is None:
        year_step = 5
    if decay_rate is None:
        decay_rate = 30
    print(num_results)

    """
    Take a CSV file worth of data, get the top results, apply exponential decay,
    and write the decayed scores back to CSV

    The file should:
        Be a CSV
        Have a 1st column named 'title'
        Have subsequent columns as single year values - i.e. 1900

    Run this 'main' script from the root scaling-science folder


    Parameters
    ----------
    filename: string
        The name of the CSV file

    num_results: int
        The number of top results you would like to return

    decay_rate: int
        The rate at which you would like scores to decay.
        **Note - smaller decay_rate values make scores decay *quicker*

    year_step: int
        The number of years between each column
        For example, if columns go 1900, 1905, 1910, year_step should be 5

    Returns
    -------
    CSV of floats with same column structure as that which was put in
        Where each float represents the decayed impact value for that paper in that year

    """

    df = data_prep(filename, num_results)
    update(df, decay_rate, year_step)
    print('Finished writing results')

if __name__ == "__main__":
    main()